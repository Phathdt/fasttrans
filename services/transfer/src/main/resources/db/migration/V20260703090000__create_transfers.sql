-- V20260703090000__create_transfers.sql
-- Main table storing transfer state; idempotency_key ensures safe replay.
CREATE TABLE transfers (
    id               uuid          PRIMARY KEY,
    user_id          uuid          NOT NULL,
    idempotency_key  text          NOT NULL,       -- from the Idempotency-Key header; dedups creation
    from_account_ref text          NOT NULL,       -- 12-digit account ref (public), NOT the account UUID
    to_account_ref   text          NOT NULL,
    amount           bigint        NOT NULL CHECK (amount > 0),   -- minor units (VND: 1 = 1 dong)
    currency         varchar(3)    NOT NULL,
    status           varchar(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING|COMPLETED|FAILED
    reason           varchar(50),                                -- null when COMPLETED
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfers_user_idem UNIQUE (user_id, idempotency_key)  -- replay → same row
);

CREATE INDEX idx_transfers_user ON transfers (user_id, created_at DESC);
