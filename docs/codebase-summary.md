# FastTrans Codebase Summary

## Project Overview

FastTrans is a demonstration money-transfer system built with a microservices architecture. It showcases event-driven messaging patterns combined with synchronous RPC, implementing Clean Architecture and Domain-Driven Design principles across all services.

**Tech Stack:**
- Backend: Java 21 + Spring Boot 3.3.4 (3 services)
- Frontend: React + Vite + TypeScript + Tailwind CSS
- Messaging: Redpanda (Kafka-compatible)
- Databases: PostgreSQL (3 separate schemas)
- Sync RPC: gRPC
- Infrastructure: Docker Compose, Traefik reverse proxy

## Services Architecture

### Unified Package Structure (Clean Architecture / DDD)

All three Java services follow the same layered architecture enforced by ArchUnit:

```
service/
  ├─ domain/              (pure business logic, framework-free)
  │  ├─ entities/         (POJOs: User, Transfer, Account, LedgerEntry)
  │  ├─ interfaces/       (repository + service contracts)
  │  └─ exception/        (domain-level exceptions)
  ├─ application/         (use cases, data transfer)
  │  ├─ dto/              (REST request/response, Kafka event payloads)
  │  └─ services/         (@Service, @Transactional orchestration)
  └─ infrastructure/      (tech-specific implementations)
     ├─ web/              (REST controllers)
     ├─ grpc/             (gRPC server/client)
     ├─ messaging/        (Kafka consumers, outbox relay)
     ├─ persistence/      (JPA entities, repositories, MapStruct mappers)
     ├─ config/           (Spring configuration)
     ├─ security/         (JWT, auth)
     └─ session/          (Redis)
```

**Dependency Rule**: `infrastructure → application → domain`. Enforced by ArchUnit tests in each service. Domain must never depend on Spring, JPA, or other frameworks.

### Auth Service (`services/auth`)

**Purpose**: User authentication and session management for Traefik ForwardAuth.

**Interfaces**:
- REST API (HTTP 8080): `POST /auth/login`, `GET /auth/verify`
- Redis session store: TTL-based revocation
- Internal only

**Key Components**:
- `domain.entities.User`: POJO (id, username, passwordHash)
- `domain.interfaces.UserRepository`: contract for persistence
- `domain.interfaces.TokenService`: contract for JWT ops
- `domain.interfaces.SessionStore`: contract for session storage
- `application.services.AuthService`: login/verify orchestration
- `infrastructure.security.JwtTokenService`: JWT implementation (jjwt library)
- `infrastructure.session.RedisSessionStore`: session storage (Redis)
- `infrastructure.persistence.*`: UserJpaEntity, repositories, MapStruct mapper
- `infrastructure.web.AuthController`: REST endpoints

**Database**: `auth_db` (users table)

**No gRPC or Kafka.** Auth is HTTP-only.

### Transfer Service (`services/transfer`)

**Purpose**: Transfer lifecycle management, ownership validation, and async ledger updates.

**Interfaces**:
- REST API (HTTP 8080): `POST /transfers`, `GET /transfers/{id}`, `GET /transfers`, `GET /accounts`
- gRPC client: calls account service for `ValidateOwnership` and `ListAccounts`
- Kafka producer: emits `transfer.requested` events
- Kafka consumer: consumes `transfer.result` events

**Key Patterns**:
- **Idempotency**: Idempotency-Key header, deduplicated by `(user_id, idempotency_key)` unique constraint
- **Transactional Outbox**: transfer + outbox row in one transaction; relay polls and publishes
- **Inbox**: `processed_messages` dedup on Kafka `messageId` prevents double-processing

**Key Components**:
- `domain.entities.Transfer`: POJO (id, userId, from/toAccountRef, amount, status)
- `domain.interfaces.TransferRepository`, `OutboxRepository`, `ProcessedMessageRepository`
- `domain.interfaces.AccountServiceClient`: gRPC contract
- `application.services.TransferService`: create/list/get orchestration
- `application.services.OutboxRelayService`: scheduled outbox relay
- `infrastructure.web.TransferController`: REST endpoints
- `infrastructure.grpc.AccountServiceGrpcClient`: gRPC async stub
- `infrastructure.messaging.TransferResultConsumer`: Kafka consumer
- `infrastructure.persistence.*`: entities, repositories, MapStruct mappers

**Database**: `transfer_db` (transfers, outbox, processed_messages tables)

**Outbox Relay**: `@Scheduled(fixedDelay=1000ms)` polls outbox, publishes via KafkaTemplate, updates status. At-least-once delivery (via inbox dedup on consumer side).

### Account Service (`services/account`)

**Purpose**: Account and ledger authority. Validates ownership, applies debit/credit, and maintains double-entry ledger.

**Interfaces**:
- gRPC server (port 9090): `ValidateOwnership`, `ListAccounts` (read-only, no transaction)
- Kafka consumer: consumes `transfer.requested`, applies ledger
- Kafka producer: emits `transfer.result` via outbox relay
- No REST endpoints (intentionally avoids spring-boot-starter-web)

**Key Patterns**:
- **Double-Entry Ledger**: `ledger_entries` (DEBIT/CREDIT) with `balance_after` snapshot
- **Balance Cache**: `accounts.balance` = sum of ledger; updated in same transaction as insert
- **Inbox Dedup**: `processed_messages` (messageId PK) prevents reprocessing of same `transfer.requested`
- **Account Locking**: `SELECT ... FOR UPDATE` on from/to accounts (sorted by UUID to prevent deadlock)

**Key Components**:
- `domain.entities.Account`, `LedgerEntry`: POJOs
- `domain.interfaces.AccountRepository`, `LedgerRepository`, `OutboxRepository`, `ProcessedMessageRepository`
- `application.services.AccountService`: gRPC impl + ledger queries
- `application.services.OutboxRelayService`: scheduled relay for results
- `infrastructure.grpc.AccountServiceImpl`: gRPC service (ValidateOwnership, ListAccounts)
- `infrastructure.messaging.TransferRequestedConsumer`: Kafka consumer
- `infrastructure.persistence.*`: JPA entities, repositories, MapStruct mappers

**Database**: `account_db` (accounts, ledger_entries, outbox, processed_messages tables)

## Event Flow

### Transfer Creation → Result

```
1. POST /transfers (Transfer service)
   ├─ gRPC ValidateOwnership(userId, fromAccountRef) → Account service
   │  └─ if false → 403 Forbidden
   ├─ (user_id, idempotency_key) lookup → dedup or create new
   ├─ INSERT transfer (PENDING) + outbox row (transfer.requested)
   ├─ Outbox relay → Kafka transfer.requested (key=fromAccountRef)
   
2. transfer.requested consumed (Account service)
   ├─ IF messageId in processed_messages → skip (idempotent)
   ├─ Resolve from/to account_ref → UUIDs
   ├─ SELECT ... FOR UPDATE on accounts (sorted)
   ├─ IF sufficient funds:
   │  ├─ INSERT ledger DEBIT(from) + CREDIT(to)
   │  ├─ UPDATE accounts.balance
   │  └─ Outbox COMPLETED
   ├─ ELSE:
   │  └─ Outbox FAILED (INSUFFICIENT_FUNDS)
   ├─ INSERT processed_messages(messageId)
   ├─ Outbox relay → Kafka transfer.result
   
3. transfer.result consumed (Transfer service)
   ├─ IF messageId in processed_messages → skip (idempotent)
   ├─ UPDATE transfer.status = result.status
   ├─ INSERT processed_messages(messageId)
```

### Fault Tolerance

- **Outbox**: at-least-once (crash between send + status update = re-publish)
- **Inbox**: dedup on `messageId` absorbs duplicates
- **Idempotency Key**: re-submit same Idempotency-Key = same transfer returned (no duplicate)

## Database Schema

### auth_db
- `users` (id, username, password_hash, created_at)

### transfer_db
- `transfers` (id, user_id, idempotency_key [unique with user_id], from/to_account_ref, amount, currency, status, reason)
- `outbox` (id, aggregate_id, topic, msg_key, payload [JSONB], status, created_at, sent_at)
- `processed_messages` (message_id, processed_at)

### account_db
- `accounts` (id, account_ref [unique], user_id, owner_name, balance, currency, updated_at)
- `ledger_entries` (id, account_id, transfer_id, direction [DEBIT/CREDIT], amount, balance_after)
- `outbox` (id, aggregate_id, topic, msg_key, payload [JSONB], status, created_at, sent_at)
- `processed_messages` (message_id, transfer_id, processed_at)

Seed: alice (2 accounts), bob (1 account), pre-populated with balances.

## Frontend

**Tech**: React 19 + Vite + TypeScript + Tailwind CSS + shadcn/ui

**Key Features**:
- Type-safe API client generated by orval + react-query + zod (from merged OpenAPI spec)
- Traefik-routed: `/api/auth/*` → Auth service, `/api/transfers`, `/api/accounts` → Transfer service
- Session management via JWT (token stored in localStorage, included in Authorization header)

**Build**:
- `pnpm dev`: Vite dev server
- `pnpm build`: TypeScript check + Vite build
- `pnpm generate:api`: orval regenerates typed client from `docs/openapi.yaml`

## Build & Deployment

**Docker Compose Stack**:
- `auth` (Spring Boot 8080)
- `transfer` (Spring Boot 8080)
- `account` (Spring Boot, gRPC 9090)
- `auth_db` (PostgreSQL)
- `transfer_db` (PostgreSQL)
- `account_db` (PostgreSQL)
- `redis` (session store)
- `redpanda` (Kafka-compatible)
- `traefik` (reverse proxy, ForwardAuth to auth service)
- `frontend` (Node.js + Vite, served via Traefik)

**Build Commands**:
```bash
# All services
docker compose up --build

# Reset state
docker compose down -v

# Per-service (from service dir)
mvn clean package

# Frontend
pnpm install && pnpm build
```

**CI/CD**:
- E2E smoke test: `bash scripts/e2e-smoke.sh` (19 checks)

## Development Conventions

- **Money**: stored as `bigint` (smallest unit, e.g., 1 VND = 1). Never floats.
- **Account Identity**: public `accountRef` (12-digit) in APIs/events; internal UUIDs only in DBs.
- **Idempotency**: Idempotency-Key header required on POST /transfers.
- **DB Migrations**: Flyway, `V<timestamp>__<description>.sql`, do not edit applied migrations.
- **API Client**: `src/api/generated/` auto-generated by orval; do not hand-edit.
- **Code Style**: Java package-by-clean-architecture; TS kebab-case files; SQL migrations snake_case.

## Testing

- ArchUnit: enforces clean-architecture dependency rules (each service)
- E2E Smoke: 19 integration checks (gRPC, Kafka, idempotency, ledger validation, etc.)
- Unit/Integration: Maven surefire, run via `mvn test` or `mvn verify`

## Metrics

**Codebase Size** (repomix):
- 169 files, ~77k tokens, ~333k chars
- Largest: generated API client (3k tokens), E2E script (2.6k), docs (2.2–2.3k each)

**Services**:
- 3 Java services (clean architecture, ArchUnit enforced)
- 1 React frontend (type-safe API client)
- Fully containerized, event-driven with inbox/outbox pattern

## Key Contracts (Source of Truth)

- **Events**: `docs/events/transfer-events.md`
- **DB Schema + Seed**: `docs/db/schema.md`
- **gRPC Proto**: `libs/fasttrans-grpc-contract/src/main/proto/account.proto`
- **Diagrams**: `docs/diagrams.md` (Mermaid)
- **OpenAPI**: `docs/openapi.yaml` (merged from services)
