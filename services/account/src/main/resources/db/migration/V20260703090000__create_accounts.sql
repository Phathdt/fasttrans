-- V20260703090000__create_accounts.sql
-- accounts table: source of truth for ownership + balance cache.
-- account_ref (public 12-digit) is the only identifier exposed externally.
CREATE TABLE accounts (
    id          uuid         PRIMARY KEY,
    account_ref text         NOT NULL UNIQUE,
    user_id     uuid         NOT NULL,
    owner_name  varchar(100) NOT NULL,
    balance     bigint       NOT NULL DEFAULT 0 CHECK (balance >= 0),
    currency    varchar(3)   NOT NULL DEFAULT 'VND',
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_user ON accounts (user_id);

-- Seed (balance matches the seed contract; fixed UUIDs to keep auth <-> account consistent)
INSERT INTO accounts (id, account_ref, user_id, owner_name, balance, currency) VALUES
  ('aaaaaaa1-0000-0000-0000-000000000001', '100000000001', '11111111-1111-1111-1111-111111111111', 'alice', 1000000, 'VND'),
  ('aaaaaaa2-0000-0000-0000-000000000002', '100000000002', '11111111-1111-1111-1111-111111111111', 'alice',   50000, 'VND'),
  ('bbbbbbb1-0000-0000-0000-000000000001', '200000000001', '22222222-2222-2222-2222-222222222222', 'bob',          0, 'VND');
