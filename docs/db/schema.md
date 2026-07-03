# Database Schema — Transfer System

Source of truth for the schema across 3 databases. Each service has its own db inside a single Postgres container. Each service's Flyway manages the migrations for its own db. Money uses `bigint` — an integer in the smallest unit (VND has no fractional part, so 1 = 1 dong), avoiding decimal rounding errors entirely. Mapped to `long` in Java. Fixed UUIDs for the seed so auth ↔ account stay consistent.

Migration naming: use a timestamp-style version `V<YYYYMMDDHHMMSS>__<description>.sql` (not sequential V1/V2) — avoids ordering conflicts when adding a new migration. The timestamps below are examples based on 2026-07-03, increasing within each service.

## Seed contract (fixed UUIDs)

| Entity | ID (internal UUID) | account_ref (public) | Note |
|---|---|---|---|
| user alice | `11111111-1111-1111-1111-111111111111` | — | password: `password` (BCrypt hash in migration) |
| user bob | `22222222-2222-2222-2222-222222222222` | — | password: `password` |
| account A1 (alice) | `aaaaaaa1-0000-0000-0000-000000000001` | `100000000001` | balance 1,000,000 VND |
| account A2 (alice) | `aaaaaaa2-0000-0000-0000-000000000002` | `100000000002` | balance 50,000 VND |
| account B1 (bob) | `bbbbbbb1-0000-0000-0000-000000000001` | `200000000001` | balance 0 VND |

`user_id` in `account_db.accounts` points to `auth_db.users.id` (cross-db logic, no physical FK).

**account_ref**: a random 12-digit public identifier (unique), generated when an account is created. It is the **only** identifier exposed outside the account service — FE, transfer API, events, and gRPC all use `account_ref`, NOT the internal UUID. The account_db UUID is used internally only (PK + ledger FK). The seed account_ref values above are fixed for the demo (in reality a random 12-digit value).

---

## auth_db (auth service)

```sql
-- V20260703090000__create_users.sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- gen_random_uuid if needed

CREATE TABLE users (
    id            uuid PRIMARY KEY,
    username      varchar(50)  NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,          -- BCrypt
    created_at    timestamptz  NOT NULL DEFAULT now()
);

-- Seed (BCrypt hash of 'password'; replace with a real hash when generating)
INSERT INTO users (id, username, password_hash) VALUES
  ('11111111-1111-1111-1111-111111111111', 'alice',
   '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5sFZ0mFq0mFq0mFq0mFq0mFq0mFqO'),
  ('22222222-2222-2222-2222-222222222222', 'bob',
   '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5sFZ0mFq0mFq0mFq0mFq0mFq0mFqO');
```

auth does NOT hold account_id — one user has N accounts, and ownership is managed by the account service. The JWT claim only contains `sub = userId`.

---

## transfer_db (transfer service)

```sql
-- V20260703090000__create_transfers.sql
CREATE TABLE transfers (
    id              uuid PRIMARY KEY,
    user_id         uuid          NOT NULL,
    idempotency_key  text         NOT NULL,       -- from Idempotency-Key header; dedup create
    from_account_ref text         NOT NULL,       -- public account ref (12-digit, not internal UUID)
    to_account_ref   text         NOT NULL,
    amount           bigint       NOT NULL CHECK (amount > 0),   -- minor units (VND: 1 = 1 dong)
    currency        varchar(3)    NOT NULL,
    status          varchar(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING|COMPLETED|FAILED
    reason          varchar(50),                                -- null when COMPLETED
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfers_user_idem UNIQUE (user_id, idempotency_key)  -- replay → same row
);
CREATE INDEX idx_transfers_user ON transfers (user_id, created_at DESC);

-- V20260703090100__create_outbox.sql  (Transactional Outbox — publish transfer.requested)
CREATE TABLE outbox (
    id           uuid PRIMARY KEY,
    aggregate_id uuid        NOT NULL,   -- transferId
    topic        varchar(50) NOT NULL,   -- 'transfer.requested'
    msg_key      varchar(100) NOT NULL,  -- partition key = from_account_ref
    payload      jsonb       NOT NULL,   -- event JSON (includes messageId)
    status       varchar(10) NOT NULL DEFAULT 'PENDING',  -- PENDING|SENT
    created_at   timestamptz NOT NULL DEFAULT now(),
    sent_at      timestamptz
);
CREATE INDEX idx_outbox_pending ON outbox (status, created_at) WHERE status = 'PENDING';
-- msg_key = from_account_ref (partition key)

-- V20260703090200__create_processed_messages.sql  (Inbox — dedup transfer.result)
CREATE TABLE processed_messages (
    message_id   uuid PRIMARY KEY,       -- messageId from the event
    processed_at timestamptz NOT NULL DEFAULT now()
);
```

---

## account_db (account service)

```sql
-- V20260703090000__create_accounts.sql
CREATE TABLE accounts (
    id          uuid         PRIMARY KEY,
    account_ref text         NOT NULL UNIQUE,   -- public id, 12-digit random; exposed outside instead of the UUID
    user_id     uuid         NOT NULL,   -- owner; ownership source of truth for gRPC
    owner_name  varchar(100) NOT NULL,
    balance     bigint       NOT NULL DEFAULT 0 CHECK (balance >= 0),  -- minor units
    currency    varchar(3)   NOT NULL DEFAULT 'VND',
    updated_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_accounts_user ON accounts (user_id);
-- account_ref: generate a random 12-digit value when creating an account; UNIQUE. The seed uses the fixed values below.

-- Seed (balances match the seed contract; VND, 1 = 1 dong)
INSERT INTO accounts (id, account_ref, user_id, owner_name, balance, currency) VALUES
  ('aaaaaaa1-0000-0000-0000-000000000001', '100000000001', '11111111-1111-1111-1111-111111111111', 'alice', 1000000, 'VND'),
  ('aaaaaaa2-0000-0000-0000-000000000002', '100000000002', '11111111-1111-1111-1111-111111111111', 'alice',   50000, 'VND'),
  ('bbbbbbb1-0000-0000-0000-000000000001', '200000000001', '22222222-2222-2222-2222-222222222222', 'bob',          0, 'VND');

-- V20260703090100__create_ledger_entries.sql  (Double-entry — balance source of truth)
CREATE TABLE ledger_entries (
    id           uuid PRIMARY KEY,
    account_id   uuid          NOT NULL REFERENCES accounts(id),
    transfer_id  uuid          NOT NULL,
    direction    varchar(6)    NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
    amount       bigint        NOT NULL CHECK (amount > 0),   -- minor units
    balance_after bigint       NOT NULL,   -- balance snapshot after this entry (audit)
    created_at   timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_account ON ledger_entries (account_id, created_at);
CREATE INDEX idx_ledger_transfer ON ledger_entries (transfer_id);

-- V20260703090200__create_processed_messages.sql  (Inbox — dedup transfer.requested)
CREATE TABLE processed_messages (
    message_id   uuid PRIMARY KEY,
    transfer_id  uuid NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now()
);

-- V20260703090300__create_outbox.sql  (Transactional Outbox — publish transfer.result)
CREATE TABLE outbox (
    id           uuid PRIMARY KEY,
    aggregate_id uuid        NOT NULL,   -- transferId
    topic        varchar(50) NOT NULL,   -- 'transfer.result'
    msg_key      varchar(100) NOT NULL,  -- key = transferId
    payload      jsonb       NOT NULL,
    status       varchar(10) NOT NULL DEFAULT 'PENDING',
    created_at   timestamptz NOT NULL DEFAULT now(),
    sent_at      timestamptz
);
CREATE INDEX idx_outbox_pending ON outbox (status, created_at) WHERE status = 'PENDING';
```

### Invariant
- `accounts.balance` = sum of `CREDIT − DEBIT` over `ledger_entries` with the same account_id. Updated only in the same transaction as the ledger insert.
- A COMPLETED transfer produces exactly 2 ledger rows: DEBIT(from) + CREDIT(to).
- Debit-credit + insert processed_messages + insert outbox all in one transaction; lock accounts with `SELECT ... FOR UPDATE` in sorted-UUID order to avoid deadlock.
- API idempotency: `transfers.idempotency_key` is UNIQUE per `(user_id, idempotency_key)`. Duplicate key → no new insert, return the existing transfer (queried by key). No separate table needed.
- account_ref vs UUID: `account_ref` (public, 12-digit text) is the only identifier exposed outside — transfer_db, events, gRPC, and FE all use the ref. account_db resolves `account_ref → id (UUID)` when writing `ledger_entries` (FK uses the internal UUID). The transfer service never sees the account UUID.

---

## Event & gRPC contract (reference)
- Event schema: `docs/events/transfer-events.md`.
- gRPC proto: `proto/account.proto` (ValidateOwnership, ListAccounts).
