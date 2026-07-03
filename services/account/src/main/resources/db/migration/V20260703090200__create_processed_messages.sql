-- V20260703090200__create_processed_messages.sql
-- Inbox dedup — prevents duplicate processing of transfer.requested with the same messageId.
CREATE TABLE processed_messages (
    message_id   uuid PRIMARY KEY,
    transfer_id  uuid NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now()
);
