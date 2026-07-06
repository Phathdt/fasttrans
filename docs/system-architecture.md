# FastTrans System Architecture

## Overview

FastTrans is a distributed microservices system implementing event-driven architecture with synchronous validation gates. The design emphasizes eventual consistency for financial ledger updates while maintaining strong consistency for ownership validation.

**Key Principle**: Fast ownership validation (gRPC sync) + reliable ledger updates (Kafka async) = responsive UX + strong guarantees.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Browser (React SPA)                        │
│         TypeScript + Vite + Tailwind + shadcn/ui               │
│              Type-safe API client (orval + zod)                 │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTP/HTTPS
                               ▼
                    ┌──────────────────────┐
                    │     Traefik 80/443   │  (Reverse Proxy)
                    │ ┌──────────────────┐ │
                    │ │  ForwardAuth     │ │  All /api/* → auth/verify
                    │ └──────────────────┘ │
                    └──────┬───────┬───────┘
                   /api/auth  |    /api/transfers, /api/accounts
                           │ ▼                        ▼
           ┌───────────────────────┐    ┌──────────────────────────┐
           │   Auth Service        │    │  Transfer Service        │
           │  (Spring Boot 8080)   │    │  (Spring Boot 8080)      │
           │                       │    │                          │
           │ ├─ POST /login        │    │ ├─ POST /transfers       │
           │ └─ GET /verify        │    │ ├─ GET /transfers        │
           │                       │    │ ├─ GET /transfers/{id}   │
           │ Domain: User          │    │ └─ GET /accounts (proxy) │
           │ Persistence: auth_db  │    │                          │
           │ Session: Redis        │    │ Domain: Transfer         │
           └───────────────────────┘    │ Persistence: transfer_db │
                       │                │ gRPC client: account     │
                       │ X-User-Id      └──────────┬────┬──────────┘
                       │                 gRPC call │    │ Kafka events
                       └─────────────────────────────┘    │
                                                         │ transfer.requested
                                                         │
                           ┌─────────────────────────┐   │
                           │  Account Service        │◄──┘
                           │  (Spring Boot, gRPC 9090)
                           │                         │
                           │ gRPC server:            │
                           │  - ValidateOwnership    │
                           │  - ListAccounts         │
                           │                         │
                           │ Domain: Account,        │
                           │         LedgerEntry     │
                           │ Persistence: account_db │
                           └────┬─────────────────┬──┘
                                │ Kafka consumer  │
                    transfer.requested → process → transfer.result
                                │                 │
                                └────────┬────────┘
                                         ▼
                              ┌──────────────────────┐
                              │  Redpanda (Kafka)    │
                              │  Topics:             │
                              │  - transfer.requested│
                              │  - transfer.result   │
                              └──────────────────────┘
```

## Service Topology

### Auth Service (services/auth)

**Role**: Identity + session gateway.

**Interfaces**:
- REST: POST `/auth/login` (username/password → JWT + session)
- REST: GET `/auth/verify` (Traefik ForwardAuth endpoint)
- Redis: Session store (key `session:<token>`, TTL = JWT expiry)

**Data Layer**:
- `auth_db` (PostgreSQL): `users` table (id, username, password_hash)
- Redis: Session cache (in-memory, TTL-based revocation)

**No gRPC or Kafka**: Auth is synchronous-only, does not participate in event flow.

**Key Algorithm** (login):
```
1. Find user by username
2. Verify password vs BCrypt hash
3. Generate JWT (HS256, sub=userId, exp=now+24h)
4. Save session key in Redis (session:<token> = userId, TTL=expiry)
5. Return token + expiresIn
```

**Key Algorithm** (verify):
```
1. Parse JWT (signature verification)
2. Check if session:<token> still in Redis (enables revocation)
3. Return X-User-Id header (userId from JWT)
```

### Transfer Service (services/transfer)

**Role**: Transfer lifecycle orchestrator.

**Interfaces**:
- REST: `POST /transfers` — create transfer (with Idempotency-Key)
- REST: `GET /transfers` — list user's transfers
- REST: `GET /transfers/{id}` — transfer detail
- REST: `GET /accounts` — proxy to account service (gRPC ListAccounts)
- gRPC client: Calls account service for `ValidateOwnership` + `ListAccounts`
- Kafka producer: Publishes `transfer.requested` via Outbox relay
- Kafka consumer: Consumes `transfer.result` from account service

**Data Layer**:
- `transfer_db`: `transfers`, `outbox`, `processed_messages`

**External Dependencies**:
- account service (gRPC, 5s timeout)
- Redpanda (Kafka)
- Redis (via Traefik-injected X-User-Id token lookup, not directly used)

**Key Algorithm** (POST /transfers):
```
1. Extract X-User-Id from request (injected by Traefik ForwardAuth)
2. gRPC ValidateOwnership(userId, fromAccountRef) → owned=?
   If false → 403 Forbidden (no DB write)
3. Look up existing transfer by (user_id, idempotency_key)
   If found → return existing (idempotent replay)
4. In one transaction:
   a. INSERT transfer (PENDING, all fields)
   b. INSERT outbox row (topic=transfer.requested, payload=[event])
   c. Catch unique constraint violation (user_id, idempotency_key) → re-read and return existing
5. Outbox relay (async, @Scheduled):
   a. SELECT * FROM outbox WHERE status='PENDING' FOR UPDATE SKIP LOCKED
   b. kafkaTemplate.send(topic, key=fromAccountRef, value=[event]).get() (block on ack)
   c. UPDATE outbox SET status='SENT', sent_at=now()
```

**Key Algorithm** (consume transfer.result):
```
1. Deserialize Kafka message (messageId, transferId, status, reason)
2. If messageId in processed_messages → skip (idempotent)
3. UPDATE transfer SET status=?, reason=? WHERE id=transferId
4. INSERT processed_messages(messageId)
```

### Account Service (services/account)

**Role**: Account authority + ledger source of truth.

**Interfaces**:
- gRPC server (port 9090): `ValidateOwnership(userId, accountRef)`, `ListAccounts(userId)`
- Kafka consumer: Consumes `transfer.requested` from transfer service
- Kafka producer: Publishes `transfer.result` via Outbox relay
- **No REST** (intentional; async-only contract, validates via gRPC)

**Data Layer**:
- `account_db`: `accounts`, `ledger_entries`, `outbox`, `processed_messages`

**No External Dependencies**: Auth + Transfer rely on account service, not vice versa.

**Key Algorithm** (gRPC ValidateOwnership):
```
1. Look up account by account_ref
2. If not found → owned=false
3. If found and user_id matches → owned=true
4. Else → owned=false
```

**Key Algorithm** (gRPC ListAccounts):
```
1. SELECT * FROM accounts WHERE user_id=?
2. Return [{ account_ref, owner_name, balance, currency }]
```

**Key Algorithm** (consume transfer.requested):
```
1. Deserialize (messageId, fromAccountRef, toAccountRef, amount, currency, transferId)
2. If messageId in processed_messages → skip (idempotent)
3. Resolve from/toAccountRef → UUID (or ACCOUNT_NOT_FOUND)
4. SELECT * FROM accounts WHERE id IN (from, to) FOR UPDATE ORDER BY id
   (lock in sorted order to prevent deadlock)
5. IF from.balance >= amount:
   a. INSERT ledger_entries (DEBIT, from, amount, balance_after=from.balance-amount)
   b. INSERT ledger_entries (CREDIT, to, amount, balance_after=to.balance+amount)
   c. UPDATE accounts SET balance=... WHERE id IN (from, to)
   d. INSERT outbox (transfer.result, COMPLETED)
ELSE:
   a. INSERT outbox (transfer.result, FAILED, reason=INSUFFICIENT_FUNDS)
6. INSERT processed_messages(messageId, transferId)
7. Outbox relay: publish transfer.result (same as transfer service)
```

## Communication Patterns

### 1. Synchronous (gRPC)

**transfer → account**: Validation gate  
**Latency**: ~5ms (local) + network  
**Timeout**: 5 seconds (deadline)  
**Failure Mode**: 503 Service Unavailable (account down = transfer POST fails, client can retry with same Idempotency-Key)

```
Transfer service: ValidateOwnership(userId=alice, accountRef=100000000001)
            ↓ gRPC stub (async, block on response)
Account service: Find account by ref, return owned=(user_id==alice)
            ↓
Transfer service: If owned=false → 403; else continue
```

**Why gRPC?**
- Sync validation is fast
- Ownership is read-only (no 2PC needed)
- Allows transfer POST to fail fast if account service down
- Network efficiency (protobuf + HTTP/2)

### 2. Asynchronous (Kafka)

**transfer → account**: Ledger updates  
**Latency**: ~1s (poll interval) + message broker  
**Delivery Semantic**: At-least-once (with idempotent consumer)  
**Failure Mode**: Delayed ledger update (transparent to client; `GET /transfers/{id}` polls until COMPLETED/FAILED)

```
Transfer service: INSERT transfer + outbox in one TX
            ↓ (async, via scheduled relay, ~1s)
Kafka broker: Publish transfer.requested event
            ↓ (async, consumer subscribed to partition)
Account service: Consume, dedup on messageId, update ledger
            ↓ INSERT ledger + outbox in one TX
Kafka broker: Publish transfer.result event
            ↓ (async, transfer consumer subscribed)
Transfer service: Consume transfer.result, update transfer status
            ↓ INSERT processed_messages
(client polls GET /transfers/{id} → status=COMPLETED)
```

**Why Kafka?**
- Decouple ledger updates from transfer creation
- Guaranteed message ordering per account (partition key = accountRef)
- At-least-once delivery (absorb duplicates via Inbox pattern)
- Audit trail (all events in Kafka for replay/debugging)

### 3. Forwarded Auth (Traefik)

**Traefik → Auth → downstream services**  
**Flow**: Every request to `/api/*` passes through `ForwardAuth` middleware

```
Client: GET /api/transfers (Authorization: Bearer <jwt>)
             ↓
Traefik: Extract Authorization header
             ↓ (call auth /auth/verify via ForwardAuth plugin)
Auth service: Validate JWT + Redis session
             ↓ 200 OK + X-User-Id header
Traefik: Add X-User-Id header to original request
             ↓
Transfer service: Reads X-User-Id (knows caller user_id without parsing JWT again)
```

**Why Traefik ForwardAuth?**
- Single point of auth validation
- Stateless (services don't need to verify JWT; trust X-User-Id header)
- Session revocation via Redis (delete key = token invalid immediately)

## Data Flow: End-to-End Transfer

```
Step 1: Client logs in (Auth service)
  POST /api/auth/login {username, password}
  → Auth service validates + creates JWT + saves Redis session
  → Returns {token, expiresIn}

Step 2: Client requests /api/transfers (Transfer service, behind ForwardAuth)
  POST /api/transfers 
    Header: Authorization: Bearer <jwt>, Idempotency-Key: <uuid>
    Body: {fromAccountRef, toAccountRef, amount, currency}
  
  → Traefik: Call auth /auth/verify → 200 OK, X-User-Id: <alice-uuid>
  → Traefik: Proxy to transfer /api/transfers, add X-User-Id header
  
  Transfer service:
    a. Extract X-User-Id = alice-uuid
    b. gRPC account.ValidateOwnership(alice-uuid, 100000000001)
       → account service: lookup account by ref, check user_id matches
       → owned = true
    c. Look up transfer by (alice-uuid, idempotency_key)
       → not found
    d. In one transaction:
       - INSERT transfer (PENDING)
       - INSERT outbox row (transfer.requested event)
    e. Return 201 {transferId, status=PENDING}

Step 3: Outbox relay publishes event (Transfer service, scheduled, async)
  @Scheduled(fixedDelay=1s):
    a. SELECT * FROM transfer_db.outbox WHERE status=PENDING FOR UPDATE SKIP LOCKED
    b. For each row: Kafka send(topic=transfer.requested, key=fromAccountRef, payload={event})
       (block until broker ack)
    c. UPDATE outbox SET status=SENT, sent_at=now()

Step 4: Account service consumes transfer.requested (Kafka consumer)
  Consumer subscribes to transfer.requested topic
    a. Deserialize event (messageId, fromAccountRef, toAccountRef, amount, ...)
    b. Check processed_messages: if messageId exists → already processed, skip
    c. Resolve account_refs → account UUIDs
    d. SELECT ... FOR UPDATE on both accounts (sorted by UUID)
    e. Check balance: if from.balance >= amount:
       - INSERT ledger DEBIT(from), CREDIT(to)
       - UPDATE accounts.balance
       - INSERT outbox (transfer.result, COMPLETED)
       Else:
       - INSERT outbox (transfer.result, FAILED, INSUFFICIENT_FUNDS)
    f. INSERT processed_messages(messageId)
  (transaction committed)

Step 5: Outbox relay publishes transfer.result (Account service, scheduled, async)
  @Scheduled(fixedDelay=1s):
    a. SELECT * FROM account_db.outbox WHERE status=PENDING FOR UPDATE SKIP LOCKED
    b. For each row: Kafka send(topic=transfer.result, key=transferId, payload={event})
    c. UPDATE outbox SET status=SENT

Step 6: Transfer service consumes transfer.result (Kafka consumer)
  Consumer subscribes to transfer.result topic:
    a. Deserialize event (messageId, transferId, status, reason)
    b. Check processed_messages: if messageId exists → skip (idempotent replay)
    c. UPDATE transfer SET status=?, reason=? WHERE id=transferId
    d. INSERT processed_messages(messageId)
  (transaction committed)

Step 7: Client polls for completion (Transfer service)
  GET /api/transfers/<transferId>
    → Traefik adds X-User-Id, calls transfer /api/transfers/<id>
    → Transfer service: SELECT transfer WHERE id=<id> AND user_id=X-User-Id
    → Return {id, status=COMPLETED, fromAccountRef, toAccountRef, amount, ...}

Client displays "Transfer completed" ✓
```

## Consistency Guarantees

### Strong (Immediate)

1. **Ownership validation**: gRPC call blocks; transfer POST fails if account not owned
2. **Idempotency**: unique constraint (user_id, idempotency_key) + de-duplicate logic
3. **Account balance**: `balance_after` snapshot in ledger; sum(ledger) = balance (audit check)
4. **Authorization**: X-User-Id header prevents cross-user access (users cannot list others' transfers)

### Eventual (Within ~1s)

1. **Ledger updates**: Kafka event → account consumer → INSERT ledger → republish transfer.result → transfer consumer → UPDATE transfer.status
2. **Balance accuracy**: Reaches "correct" state after all events consumed (Inbox dedup ensures idempotency)
3. **Cross-service consistency**: transfer + account_db changes are separate transactions; reconciled via event flow

**Trade-off**: User creates transfer (fast), sees PENDING status (UI shows spinner), status updates after ~1-2s (when Kafka event consumed). Acceptable for demo.

### At-Least-Once Delivery (Kafka)

**Outbox Relay Can Crash**:
- Send broker ack received, but crash before UPDATE outbox SET status=SENT
- Next relay loop re-publishes same row
- Consumer sees duplicate message (same messageId)
- Inbox dedup (messageId PK) skips re-processing

**No Message Loss**:
- Outbox row persists in DB until status=SENT
- Relay will eventually publish (at least once)
- Downstream consumer is idempotent (Inbox pattern)

## Database Synchronization

### Within-Service (Single Transaction)

**Transfer service (POST /transfers)**:
```
BEGIN;
  INSERT transfer (...) → transfer_db.transfers
  INSERT outbox (...) → transfer_db.outbox
COMMIT;
```
Both or neither; no partial state.

**Account service (consume transfer.requested)**:
```
BEGIN;
  INSERT ledger_entries (...) → account_db.ledger_entries
  UPDATE accounts SET balance=... → account_db.accounts
  INSERT outbox (...) → account_db.outbox
  INSERT processed_messages (...) → account_db.processed_messages
COMMIT;
```
All ledger + balance + outbox updated together; Inbox ensures idempotency.

### Cross-Service (Event-Driven)

Transfer DB and Account DB are separate. Consistency achieved via:
1. Transactional Outbox (write side consistency)
2. Inbox Dedup (read side idempotency)
3. Polling relay (at-least-once delivery)

**No 2-Phase Commit**: Single-phase commit per service, event-driven coordination.

## Failure Modes & Recovery

### Failure: Account Service Down (gRPC timeout)

**Scenario**: transfer service calls account.ValidateOwnership, account service not responding

**Immediate**: Transfer POST returns 503 Service Unavailable  
**Client**: Sees error; can retry with same Idempotency-Key  
**State**: transfer row NOT inserted (no idempotency_key recorded)  
**Recovery**: Retry POST (will re-validate ownership, then create or return existing if already created)

### Failure: Kafka broker down

**Scenario**: Transfer created, but Kafka broker unreachable

**Immediate**: Transfer POST succeeds (outbox row inserted)  
**Outbox Relay**: Fails to publish (kafkaTemplate.send timeout)  
**Client**: Status remains PENDING (no transfer.result consumed)  
**Recovery**: Relay retries every ~1s; once Kafka recovers, outbox publishes, account processes, transfer.result received, status updates

### Failure: Ledger race condition (concurrent transfers)

**Scenario**: Two transfers from same account at same time; insufficient funds for both

**Prevention**: Account service locks both accounts (FROM, TO) in sorted UUID order  
**If concurrent overlaps**:
  - First transaction: acquires locks, updates balance, releases
  - Second transaction: blocks on locks, then checks balance (may be insufficient)
  - Outcome: one COMPLETED, one FAILED (never double-debit)

### Failure: Duplicate message (Kafka replayed)

**Scenario**: transfer.requested consumed, ledger updated, but crash before processed_messages INSERT

**Recovery on Replay**:
  - Consumer re-reads same Kafka message (offset reset)
  - messageId already in processed_messages (before crash, was inserted but not visible)
  - Consumer checks: IF messageId EXISTS → skip
  - No double-ledger entry

**Note**: Inbox dedup (messageId PK) makes re-reading safe; application layer skips if already processed.

### Failure: Redis flush (session loss)

**Scenario**: Redis server crashes, all session keys lost

**Immediate**: Next GET /auth/verify fails (session key not found)  
**Client**: Sees 401 Unauthorized; redirected to login  
**User**: Logs in again (new JWT + session key created)  
**Outstanding Transfers**: Status polled via GET /api/transfers (still readable; auth check uses X-User-Id in transfer table, not Redis)

## Deployment Topology

### Docker Compose (Development)

```
Services:
  - auth (Spring Boot, port 8080 internal, routed via Traefik)
  - transfer (Spring Boot, port 8080 internal, routed via Traefik)
  - account (Spring Boot, gRPC port 9090 internal, no HTTP exposed)

Databases:
  - postgres (3 schemas: auth_db, transfer_db, account_db)

Message Broker:
  - redpanda (Kafka API, internal port 29092, external 9092)

Session Store:
  - redis

Reverse Proxy:
  - traefik (port 80 host, 8081 dashboard)

Infrastructure:
  - flyway (DB migration, runs on startup)
  - redpanda-init (Kafka topic creation, runs once)
```

**Health Checks**:
- Each service: HTTP GET /actuator/health (Spring Boot Actuator)
- postgres / redis / redpanda: Native health probes
- Traefik: Healthy when backends respond

### Production (AWS / Cloud Native)

**Suggested**:
- ECS/EKS for service orchestration (replicas per service)
- RDS PostgreSQL (managed, multi-AZ, backups)
- ElastiCache Redis (managed, with persistence)
- MSK (Managed Streaming for Kafka) or Confluent Cloud
- ALB or API Gateway (instead of Traefik) for ForwardAuth
- CloudWatch for logs / metrics
- X-Ray for distributed tracing

**Changes**:
- Outbox relay: Multi-instance with `FOR UPDATE SKIP LOCKED` (only one instance publishes per row)
- gRPC: Add circuit breaker + retries (Resilience4j)
- Kafka consumer: Parallel processing per partition (concurrency > 1)
- Secrets: AWS Secrets Manager or HashiCorp Vault (not env vars)

## Security Posture

### Implemented
- JWT signature verification (HS256)
- Password hashing (BCrypt)
- Session revocation (Redis-backed)
- X-User-Id header prevents cross-user access (enforced in application.services)
- SQL: Parameterized queries (Spring Data, ORM)
- No PII in logs

### Out of Scope (Demo Only)
- HTTPS/TLS (local docker compose)
- API rate limiting
- gRPC mTLS
- Kafka authentication (no SASL)
- Database encryption at rest
- Audit logging (events logged to Kafka, not dedicated audit trail)

## Scalability Considerations

### Horizontal Scaling

**Transfer service**: Stateless; each instance handles POST/GET independently. Outbox relay `FOR UPDATE SKIP LOCKED` prevents duplicate publish.

**Account service**: Stateless gRPC server; Kafka consumer group allows parallel consumption. Lock contention on ledger (per-account sequential updates) is inherent to double-entry accounting.

**Bottleneck**: Account service Kafka consumer (single partition per account, so single consumer thread per account). Mitigation: partition by account_ref (already done: key=fromAccountRef on transfer.requested).

### Vertical Scaling

**Database**: Connection pooling (HikariCP, 10 connections per service). Tuning: pool size, statement cache size.

**Kafka**: Throughput bounded by broker; scale via broker replicas + more partitions.

### Cache Optimization

**Accounts**: Could cache ListAccounts response (Redis); invalidate on transfer.result consumed. Not implemented (demo).

**Transfer lookup**: Could cache by idempotency_key; risk: stale cache if transfer updated. Not recommended for financial systems.

## Observability

### Metrics (Future)

- Transfer latency (create, consume, complete)
- Kafka lag (consumer offset vs. latest)
- Database pool utilization
- gRPC call duration + errors

### Logging

- Application logs to stdout (Docker captures)
- Structured: SLF4J with {} placeholders
- Log level: INFO for important events, DEBUG for detail

### Tracing (Future)

- Correlation ID across REST → gRPC → Kafka → REST chain
- Distributed tracing backend (Jaeger, Datadog)

## Related Documentation

- **Events**: `docs/events/transfer-events.md` (Kafka message schemas)
- **Database**: `docs/db/schema.md` (DDL, indexes, constraints)
- **gRPC**: `libs/fasttrans-grpc-contract/src/main/proto/account.proto` (protobuf definition)
- **Code Standards**: `docs/code-standards.md` (3-layer structure, naming)
- **Services**: `docs/services/{auth,transfer,account}-service.md` (package layout, API detail)

---

**Last Updated**: 2026-07-04  
**Architecture Snapshot**: Clean Architecture 3-layer, Transactional Outbox + Inbox, gRPC + Kafka hybrid
