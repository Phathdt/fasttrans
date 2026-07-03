# Transfer Service

Owns transfer lifecycle. Validates account ownership synchronously via gRPC, then drives debit/credit asynchronously via Redpanda. Exposes transfer REST APIs plus an accounts proxy.

- Plan phase: [phase-03-transfer-service.md](../../plans/260703-1537-java-microservices-transfer-demo/phase-03-transfer-service.md)
- Stack: Spring Boot (Web, Data JPA, Kafka), Postgres `transfer_db`, Redpanda, gRPC client.
- Port: HTTP 8080 (internal, via Traefik).
- Patterns: API idempotency key, Transactional Outbox (produce), Inbox (consume).

## Database — transfer_db

```sql
CREATE TABLE transfers (
    id              uuid PRIMARY KEY,
    user_id         uuid          NOT NULL,
    idempotency_key  text         NOT NULL,       -- Idempotency-Key header; dedup create
    from_account_ref text         NOT NULL,       -- public account ref (not internal UUID)
    to_account_ref   text         NOT NULL,
    amount           bigint       NOT NULL CHECK (amount > 0),   -- minor units
    currency        varchar(3)    NOT NULL,
    status          varchar(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING|COMPLETED|FAILED
    reason          varchar(50),                                -- null when COMPLETED
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfers_user_idem UNIQUE (user_id, idempotency_key)  -- replay → same row
);
CREATE INDEX idx_transfers_user ON transfers (user_id, created_at DESC);

CREATE TABLE outbox (                    -- produces transfer.requested
    id           uuid PRIMARY KEY,
    aggregate_id uuid         NOT NULL,  -- transferId
    topic        varchar(50)  NOT NULL,  -- 'transfer.requested'
    msg_key      varchar(100) NOT NULL,  -- partition key = from_account_ref
    payload      jsonb        NOT NULL,  -- event JSON (includes messageId)
    status       varchar(10)  NOT NULL DEFAULT 'PENDING',  -- PENDING|SENT
    created_at   timestamptz  NOT NULL DEFAULT now(),
    sent_at      timestamptz
);
CREATE INDEX idx_outbox_pending ON outbox (status, created_at) WHERE status = 'PENDING';

CREATE TABLE processed_messages (        -- inbox dedup for transfer.result
    message_id   uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);
```

Full schema + seed: [db/schema.md](../db/schema.md).

## REST API

Routed via Traefik behind ForwardAuth (`/api/transfers/*`, `/api/accounts`). Gateway injects `X-User-Id`.

### POST /transfers
Create a transfer. Ownership of `fromAccountRef` is validated via gRPC before any write.

Request headers: `X-User-Id` (gateway), `Idempotency-Key: <uuid>` (client, required).
Request body:
```json
{ "fromAccountRef": "100000000001", "toAccountRef": "200000000001", "amount": 100000, "currency": "VND" }
```
Response `201`:
```json
{ "id": "<transferId>", "status": "PENDING" }
```
Errors:
- `400` missing `Idempotency-Key`.
- `403` `fromAccountRef` not owned by the user (gRPC `ValidateOwnership` returns false).
- `503` account service unavailable (gRPC deadline exceeded).
- Same `Idempotency-Key` replay (same user) → returns the existing transfer, no new row.

### GET /transfers
List the caller's transfers, newest first.

Response `200`:
```json
[ { "id": "...", "fromAccountRef": "100000000001", "toAccountRef": "200000000001", "amount": 100000,
    "currency": "VND", "status": "COMPLETED", "reason": null, "createdAt": "..." } ]
```

### GET /transfers/{id}
Transfer detail. `404`/`403` if not owned by the caller. Used by the FE to poll PENDING → COMPLETED/FAILED.

### GET /accounts
Proxy to account service via gRPC `ListAccounts(userId)`. Lets the FE pick a `fromAccountRef`.

Response `200`:
```json
[ { "accountRef": "100000000001", "ownerName": "alice", "balance": 1000000, "currency": "VND" } ]
```

## gRPC (client)

Calls account service (`account:9090`, proto `proto/account.proto`):
- `ValidateOwnership(userId, accountRef) → {owned}` — blocking, 5s deadline, on create.
- `ListAccounts(userId) → repeated Account` — backs `GET /accounts`.

## Events (Redpanda)

- Produces `transfer.requested` (key = `fromAccountRef`) via the outbox relay.
- Consumes `transfer.result` (group `transfer-service`): dedup on `messageId` via `processed_messages`, then update transfer status only if still `PENDING`.

Event schemas: [events/transfer-events.md](../events/transfer-events.md).

## Publishing: outbox relay (polling), not CDC

The event row is written to `outbox` in the same DB transaction as the business change. A polling relay publishes it afterwards. We deliberately use an in-app polling relay, not Debezium/CDC — no extra Kafka Connect infra, and the flow is observable by querying the `outbox` table (PENDING vs SENT). CDC is out of scope.

```
@Scheduled(fixedDelay = 1000ms)   // one relay loop
1. SELECT * FROM outbox
   WHERE status = 'PENDING'
   ORDER BY created_at
   FOR UPDATE SKIP LOCKED          // safe if >1 instance runs the relay
   LIMIT 100;
2. for each row: kafkaTemplate.send(topic, msg_key, payload).get();  // block on broker ack
3. UPDATE outbox SET status = 'SENT', sent_at = now() WHERE id = ?;
```

- **At-least-once**: a crash after the broker ack but before the `SENT` update re-publishes the row on the next loop. Consumers absorb this via their inbox (`processed_messages`) dedup on `messageId`. Outbox + inbox are a pair.
- **Ordering**: publishing in `created_at` order with key = `fromAccountRef` preserves per-account ordering, matching the topic partition key.
- **Multi-instance**: `FOR UPDATE SKIP LOCKED` lets multiple relay instances poll without publishing the same row twice. For the single-instance demo it is harmless but kept for correctness.
- **Latency**: bounded by the poll interval (~1s) — acceptable for the demo; this is the trade-off vs CDC's near-real-time WAL streaming.

## Key transaction (create)
1. gRPC `ValidateOwnership(userId, fromAccountRef)`; false → 403 (no DB write).
2. Look up existing transfer by `(user_id, idempotency_key)`; hit → return that transfer (idempotent replay).
3. Else in one transaction: insert transfer PENDING (with `idempotency_key`, `from_account_ref`, `to_account_ref`) + insert outbox row (`transfer.requested`, messageId, key=fromAccountRef). The `uq_transfers_user_idem` unique constraint guards against concurrent duplicates (catch violation → re-read and return existing row).

## Acceptance
- Owned `fromAccountRef` → 201 PENDING + transfer row + outbox row.
- Not owned → 403, no rows. Missing Idempotency-Key → 400. Account down → 503.
- Replay same Idempotency-Key → original transfer, no duplicate.
- `GET /accounts` returns the user's N accounts. Consuming `transfer.result` replay (same messageId) does not double-apply.
