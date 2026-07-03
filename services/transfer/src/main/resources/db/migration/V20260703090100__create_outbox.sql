-- V20260703090100__create_outbox.sql
-- Transactional Outbox: written in the same transaction as the business data; the relay polls and publishes later.
CREATE TABLE outbox (
    id           uuid         PRIMARY KEY,
    aggregate_id uuid         NOT NULL,   -- transferId
    topic        varchar(50)  NOT NULL,   -- 'transfer.requested'
    msg_key      varchar(100) NOT NULL,   -- partition key = from_account_ref
    payload      jsonb        NOT NULL,   -- event JSON (includes messageId for consumer-side dedup)
    status       varchar(10)  NOT NULL DEFAULT 'PENDING',  -- PENDING|SENT
    created_at   timestamptz  NOT NULL DEFAULT now(),
    sent_at      timestamptz
);

CREATE INDEX idx_outbox_pending ON outbox (status, created_at) WHERE status = 'PENDING';
