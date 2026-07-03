-- V20260703090100__create_ledger_entries.sql
-- Double-entry ledger — source of truth for balances; account.balance = sum(CREDIT - DEBIT).
CREATE TABLE ledger_entries (
    id            uuid PRIMARY KEY,
    account_id    uuid          NOT NULL REFERENCES accounts(id),
    transfer_id   uuid          NOT NULL,
    direction     varchar(6)    NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
    amount        bigint        NOT NULL CHECK (amount > 0),
    balance_after bigint        NOT NULL,
    created_at    timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_account  ON ledger_entries (account_id, created_at);
CREATE INDEX idx_ledger_transfer ON ledger_entries (transfer_id);
