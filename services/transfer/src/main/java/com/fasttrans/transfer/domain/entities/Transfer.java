package com.fasttrans.transfer.domain.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Transfer — accounts referenced by public accountRef (not internal UUID).
 * Pure domain entity: no Spring/JPA/Jackson. Holds the status transition logic.
 */
public class Transfer {

    private UUID id;
    private UUID userId;
    private String idempotencyKey;
    private String fromAccountRef;
    private String toAccountRef;
    private long amount;
    private String currency;
    private TransferStatus status;
    private String reason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Transfer() {
    }

    public Transfer(UUID id, UUID userId, String idempotencyKey, String fromAccountRef,
                    String toAccountRef, long amount, String currency, TransferStatus status,
                    String reason, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.fromAccountRef = fromAccountRef;
        this.toAccountRef = toAccountRef;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Factory for a new PENDING transfer. */
    public static Transfer pending(UUID id, UUID userId, String idempotencyKey,
                                   String fromAccountRef, String toAccountRef,
                                   long amount, String currency) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Transfer(id, userId, idempotencyKey, fromAccountRef, toAccountRef,
                amount, currency, TransferStatus.PENDING, null, now, now);
    }

    /** Applies a result status (COMPLETED|FAILED) + reason. */
    public void applyResult(String status, String reason) {
        this.status = TransferStatus.valueOf(status);
        this.reason = reason;
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isPending() {
        return status == TransferStatus.PENDING;
    }

    // --- getters / setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getFromAccountRef() { return fromAccountRef; }
    public void setFromAccountRef(String fromAccountRef) { this.fromAccountRef = fromAccountRef; }

    public String getToAccountRef() { return toAccountRef; }
    public void setToAccountRef(String toAccountRef) { this.toAccountRef = toAccountRef; }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public TransferStatus getStatus() { return status; }
    public void setStatus(TransferStatus status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
