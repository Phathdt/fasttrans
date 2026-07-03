-- V20260703090200__create_processed_messages.sql
-- Inbox dedup: stores messageIds already processed from the transfer.result topic; prevents double-apply.
CREATE TABLE processed_messages (
    message_id   uuid        PRIMARY KEY,       -- messageId from TransferResultEvent
    processed_at timestamptz NOT NULL DEFAULT now()
);
