# Account Service

Source of truth for accounts, balances, and the ledger. Hosts a gRPC server (ownership + listing) for synchronous calls from the transfer service, and a Kafka consumer that applies debit/credit asynchronously. No REST.

- Plan phase: [phase-04-account-service.md](../../plans/260703-1537-java-microservices-transfer-demo/phase-04-account-service.md)
- Stack: Spring Boot (Data JPA, Kafka), Postgres `account_db`, Redpanda, gRPC server.
- Port: gRPC 9090 (internal). No HTTP.
- Patterns: double-entry ledger, Inbox (consume), Transactional Outbox (produce).

## Database — account_db

```sql
CREATE TABLE accounts (
    id          uuid         PRIMARY KEY,               -- internal only
    account_ref text         NOT NULL UNIQUE,           -- public id, 12-digit random; exposed everywhere
    user_id     uuid         NOT NULL,   -- owner; ownership source of truth for gRPC
    owner_name  varchar(100) NOT NULL,
    balance     bigint       NOT NULL DEFAULT 0 CHECK (balance >= 0),  -- minor units, cache of ledger
    currency    varchar(3)   NOT NULL DEFAULT 'VND',
    updated_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_accounts_user ON accounts (user_id);

CREATE TABLE ledger_entries (            -- double-entry, source of truth for balance
    id            uuid PRIMARY KEY,
    account_id    uuid          NOT NULL REFERENCES accounts(id),
    transfer_id   uuid          NOT NULL,
    direction     varchar(6)    NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
    amount        bigint        NOT NULL CHECK (amount > 0),   -- minor units
    balance_after bigint        NOT NULL,  -- snapshot after this entry (audit)
    created_at    timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_account  ON ledger_entries (account_id, created_at);
CREATE INDEX idx_ledger_transfer ON ledger_entries (transfer_id);

CREATE TABLE processed_messages (        -- inbox dedup for transfer.requested
    message_id   uuid PRIMARY KEY,
    transfer_id  uuid NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE outbox (                    -- produces transfer.result
    id           uuid PRIMARY KEY,
    aggregate_id uuid         NOT NULL,  -- transferId
    topic        varchar(50)  NOT NULL,  -- 'transfer.result'
    msg_key      varchar(100) NOT NULL,  -- key = transferId
    payload      jsonb        NOT NULL,
    status       varchar(10)  NOT NULL DEFAULT 'PENDING',
    created_at   timestamptz  NOT NULL DEFAULT now(),
    sent_at      timestamptz
);
CREATE INDEX idx_outbox_pending ON outbox (status, created_at) WHERE status = 'PENDING';
```

Seed accounts (fixed UUIDs + account_ref): alice → A1 ref `100000000001` (1,000,000 VND), A2 ref `100000000002` (50,000 VND); bob → B1 ref `200000000001` (0 VND). Full: [db/schema.md](../db/schema.md).

### Invariant
- `accounts.balance` = sum(`CREDIT` − `DEBIT`) over `ledger_entries` for that account. Updated only in the same transaction as the ledger insert.
- A COMPLETED transfer produces exactly 2 ledger rows: DEBIT(from) + CREDIT(to).
- **account_ref vs UUID**: `account_ref` (public, 12-digit) is the only identifier exposed outside — gRPC and events use it. Internally account_db resolves `account_ref → id (UUID)` to write `ledger_entries` (FK uses UUID). Unknown ref → `ACCOUNT_NOT_FOUND`.

## gRPC (server)

Proto `proto/account.proto`, listening on `account:9090`. Read-only, no write transaction.

### ValidateOwnership
```
ValidateOwnershipRequest  { string user_id; string account_ref; }
ValidateOwnershipResponse { bool owned; }
```
`owned = (accounts.user_id == user_id)` for the account with the given `account_ref`. Unknown ref → `owned = false`.

### ListAccounts
```
ListAccountsRequest  { string user_id; }
ListAccountsResponse { repeated Account accounts; }
Account { string account_ref; string owner_name; int64 balance; string currency; }
```
Returns all accounts where `user_id` matches. Exposes `account_ref` (not the internal UUID). `balance` is `int64` minor units (VND: 1 = 1đ).

## Events (Redpanda)

- Consumes `transfer.requested` (group `account-service`, concurrency 1 per partition).
- Produces `transfer.result` via the outbox relay.

Event schemas: [events/transfer-events.md](../events/transfer-events.md).

## Publishing: outbox relay (polling), not CDC

`transfer.result` is written to `outbox` in the same transaction as the ledger update, then published by a polling relay — same mechanism as the transfer service (no CDC/Debezium; out of scope).

```
@Scheduled(fixedDelay = 1000ms)
1. SELECT * FROM outbox
   WHERE status = 'PENDING'
   ORDER BY created_at
   FOR UPDATE SKIP LOCKED
   LIMIT 100;
2. for each row: kafkaTemplate.send(topic, msg_key, payload).get();  // block on broker ack
3. UPDATE outbox SET status = 'SENT', sent_at = now() WHERE id = ?;
```

- **At-least-once**: a crash between broker ack and the `SENT` update re-publishes `transfer.result`. The transfer service's inbox (`processed_messages`, dedup on `messageId`) absorbs the duplicate.
- **Ordering**: key = `transferId`; result ordering per transfer is preserved.
- **Multi-instance**: `FOR UPDATE SKIP LOCKED` avoids double-publish. Single instance for the demo.
- **Latency**: bounded by the poll interval (~1s).

## Key transaction (consume transfer.requested)
1. If `messageId` already in `processed_messages` → skip (idempotent), ack.
2. Resolve `fromAccountRef` / `toAccountRef` → account UUIDs. Missing ref → FAILED `ACCOUNT_NOT_FOUND`.
3. `SELECT ... FOR UPDATE` on from/to accounts (locked in sorted-UUID order to avoid deadlock).
4. Sufficient funds → insert DEBIT(from) + CREDIT(to) ledger rows (FK = internal UUID), update both balances, set `balance_after`, insert outbox `transfer.result` COMPLETED.
5. Insufficient funds → insert outbox `transfer.result` FAILED (`INSUFFICIENT_FUNDS`).
6. Always insert `processed_messages(messageId)`.

## Acceptance
- `ValidateOwnership`: owned account → true; other user's account → false.
- `ListAccounts`: alice → 2 accounts, bob → 1 account.
- Sufficient funds → 2 ledger rows + correct balances → COMPLETED.
- Insufficient → 0 ledger rows, balance unchanged → FAILED INSUFFICIENT_FUNDS.
- Replay same messageId → no extra ledger, balance unchanged.
- `sum(ledger)` per account == current balance.
