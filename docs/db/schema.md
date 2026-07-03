# Database Schema — Transfer System

Nguồn chân lý schema 3 database. Mỗi service 1 db riêng trong cùng 1 Postgres container. Flyway của từng service quản migration của db đó. Tiền dùng `bigint` — số nguyên đơn vị nhỏ nhất (VND không có phần lẻ nên 1 = 1 đồng), tránh hẳn sai số thập phân. Map sang `long` ở Java. UUID cố định cho seed để auth ↔ account không lệch.

Migration naming: dùng version dạng timestamp `V<YYYYMMDDHHMMSS>__<mô_tả>.sql` (không dùng V1/V2 tuần tự) — tránh xung đột số thứ tự khi thêm migration mới. Timestamp dưới đây là ví dụ theo ngày 2026-07-03, tăng dần trong mỗi service.

## Seed contract (fixed UUIDs)

| Entity | ID | Ghi chú |
|---|---|---|
| Entity | ID (UUID nội bộ) | account_ref (public) | Ghi chú |
|---|---|---|---|
| user alice | `11111111-1111-1111-1111-111111111111` | — | password: `password` (BCrypt hash trong migration) |
| user bob | `22222222-2222-2222-2222-222222222222` | — | password: `password` |
| account A1 (alice) | `aaaaaaa1-0000-0000-0000-000000000001` | `100000000001` | balance 1,000,000 VND |
| account A2 (alice) | `aaaaaaa2-0000-0000-0000-000000000002` | `100000000002` | balance 50,000 VND |
| account B1 (bob) | `bbbbbbb1-0000-0000-0000-000000000001` | `200000000001` | balance 0 VND |

`user_id` trong `account_db.accounts` trỏ tới `auth_db.users.id` (cross-db logic, không FK vật lý).

**account_ref**: public identifier 12 chữ số random (unique), sinh khi tạo account. Đây là identifier **duy nhất** lộ ra ngoài account service — FE, API transfer, event, gRPC đều dùng `account_ref`, KHÔNG dùng UUID nội bộ. account_db UUID chỉ dùng nội bộ (PK + FK ledger). account_ref seed ở trên là giá trị cố định cho demo (thực tế random 12 chữ số).

---

## auth_db (auth service)

```sql
-- V20260703090000__create_users.sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- gen_random_uuid nếu cần

CREATE TABLE users (
    id            uuid PRIMARY KEY,
    username      varchar(50)  NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,          -- BCrypt
    created_at    timestamptz  NOT NULL DEFAULT now()
);

-- Seed (BCrypt hash của 'password'; thay hash thật khi generate)
INSERT INTO users (id, username, password_hash) VALUES
  ('11111111-1111-1111-1111-111111111111', 'alice',
   '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5sFZ0mFq0mFq0mFq0mFq0mFq0mFqO'),
  ('22222222-2222-2222-2222-222222222222', 'bob',
   '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5sFZ0mFq0mFq0mFq0mFq0mFq0mFqO');
```

auth KHÔNG giữ account_id — 1 user có N account, ownership do account service quản. JWT claim chỉ chứa `sub = userId`.

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
    amount           bigint       NOT NULL CHECK (amount > 0),   -- minor units (VND: 1 = 1đ)
    currency        varchar(3)    NOT NULL,
    status          varchar(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING|COMPLETED|FAILED
    reason          varchar(50),                                -- null khi COMPLETED
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
    payload      jsonb       NOT NULL,   -- event JSON (gồm messageId)
    status       varchar(10) NOT NULL DEFAULT 'PENDING',  -- PENDING|SENT
    created_at   timestamptz NOT NULL DEFAULT now(),
    sent_at      timestamptz
);
CREATE INDEX idx_outbox_pending ON outbox (status, created_at) WHERE status = 'PENDING';
-- msg_key = from_account_ref (partition key)

-- V20260703090200__create_processed_messages.sql  (Inbox — dedup transfer.result)
CREATE TABLE processed_messages (
    message_id   uuid PRIMARY KEY,       -- messageId từ event
    processed_at timestamptz NOT NULL DEFAULT now()
);
```

---

## account_db (account service)

```sql
-- V20260703090000__create_accounts.sql
CREATE TABLE accounts (
    id          uuid         PRIMARY KEY,
    account_ref text         NOT NULL UNIQUE,   -- public id, 12-digit random; lộ ra ngoài thay UUID
    user_id     uuid         NOT NULL,   -- chủ sở hữu; nguồn chân lý ownership cho gRPC
    owner_name  varchar(100) NOT NULL,
    balance     bigint       NOT NULL DEFAULT 0 CHECK (balance >= 0),  -- minor units
    currency    varchar(3)   NOT NULL DEFAULT 'VND',
    updated_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_accounts_user ON accounts (user_id);
-- account_ref: sinh 12 chữ số random khi tạo account; UNIQUE. Seed dùng giá trị cố định dưới đây.

-- Seed (balance khớp seed contract; VND, 1 = 1đ)
INSERT INTO accounts (id, account_ref, user_id, owner_name, balance, currency) VALUES
  ('aaaaaaa1-0000-0000-0000-000000000001', '100000000001', '11111111-1111-1111-1111-111111111111', 'alice', 1000000, 'VND'),
  ('aaaaaaa2-0000-0000-0000-000000000002', '100000000002', '11111111-1111-1111-1111-111111111111', 'alice',   50000, 'VND'),
  ('bbbbbbb1-0000-0000-0000-000000000001', '200000000001', '22222222-2222-2222-2222-222222222222', 'bob',          0, 'VND');

-- V20260703090100__create_ledger_entries.sql  (Double-entry — nguồn chân lý số dư)
CREATE TABLE ledger_entries (
    id           uuid PRIMARY KEY,
    account_id   uuid          NOT NULL REFERENCES accounts(id),
    transfer_id  uuid          NOT NULL,
    direction    varchar(6)    NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
    amount       bigint        NOT NULL CHECK (amount > 0),   -- minor units
    balance_after bigint       NOT NULL,   -- snapshot số dư sau bút toán (audit)
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
- `accounts.balance` = tổng `CREDIT − DEBIT` của `ledger_entries` cùng account_id. Chỉ update trong cùng transaction với ledger insert.
- Transfer COMPLETED sinh đúng 2 ledger row: DEBIT(from) + CREDIT(to).
- Debit-credit + insert processed_messages + insert outbox nằm trong 1 transaction; lock account bằng `SELECT ... FOR UPDATE` theo thứ tự sort UUID để tránh deadlock.
- API idempotency: `transfers.idempotency_key` UNIQUE theo `(user_id, idempotency_key)`. Trùng key → không insert mới, trả lại transfer đã tồn tại (query theo key). Không cần bảng riêng.
- account_ref vs UUID: `account_ref` (public, text 12 số) là identifier duy nhất lộ ra ngoài — transfer_db, events, gRPC, FE đều dùng ref. account_db resolve `account_ref → id (UUID)` khi ghi `ledger_entries` (FK dùng UUID nội bộ). transfer service không bao giờ thấy UUID account.

---

## Event & gRPC contract (tham chiếu)
- Event schema: `docs/events/transfer-events.md`.
- gRPC proto: `proto/account.proto` (ValidateOwnership, ListAccounts).
