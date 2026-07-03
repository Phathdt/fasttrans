-- V20260703090300__create_outbox.sql
-- Transactional Outbox — publishes transfer.result; the polling relay reads and marks SENT.
CREATE TABLE outbox (
    id           uuid PRIMARY KEY,
    aggregate_id uuid        NOT NULL,
    topic        varchar(50) NOT NULL,
    msg_key      varchar(100) NOT NULL,
    payload      jsonb       NOT NULL,
    status       varchar(10) NOT NULL DEFAULT 'PENDING',
    created_at   timestamptz NOT NULL DEFAULT now(),
    sent_at      timestamptz
);

CREATE INDEX idx_outbox_pending ON outbox (status, created_at) WHERE status = 'PENDING';
