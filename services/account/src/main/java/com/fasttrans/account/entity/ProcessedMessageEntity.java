package com.fasttrans.account.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbox dedup — ensures idempotency when receiving transfer.requested.
 * messageId is the PK; existence is checked before processing.
 */
@Entity
@Table(name = "processed_messages")
public class ProcessedMessageEntity {

    /** messageId from the event — used as the PK for dedup. */
    @Id
    @Column(name = "message_id", columnDefinition = "uuid")
    private UUID messageId;

    @Column(name = "transfer_id", nullable = false, columnDefinition = "uuid")
    private UUID transferId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @PrePersist
    void prePersist() {
        processedAt = Instant.now();
    }

    // --- getters / setters ---

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public UUID getTransferId() { return transferId; }
    public void setTransferId(UUID transferId) { this.transferId = transferId; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
