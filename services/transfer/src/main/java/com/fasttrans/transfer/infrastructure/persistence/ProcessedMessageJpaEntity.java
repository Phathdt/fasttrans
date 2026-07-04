package com.fasttrans.transfer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// Inbox dedup: once a transfer.result messageId has been processed, skip it next time.
@Entity
@Table(name = "processed_messages")
public class ProcessedMessageJpaEntity {

    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected ProcessedMessageJpaEntity() {
    }

    public ProcessedMessageJpaEntity(UUID messageId) {
        this.messageId = messageId;
        this.processedAt = OffsetDateTime.now();
    }

    public UUID getMessageId() {
        return messageId;
    }
}
