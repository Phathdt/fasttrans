package com.fasttrans.transfer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// Transfer: accounts are referenced by accountRef (public), NOT by the account UUID.
@Entity
@Table(name = "transfers")
public class TransferEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "from_account_ref", nullable = false)
    private String fromAccountRef;

    @Column(name = "to_account_ref", nullable = false)
    private String toAccountRef;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String status;   // PENDING|COMPLETED|FAILED

    @Column
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TransferEntity() {
    }

    public static TransferEntity pending(UUID id, UUID userId, String idempotencyKey,
                                         String fromAccountRef, String toAccountRef,
                                         long amount, String currency) {
        TransferEntity t = new TransferEntity();
        t.id = id;
        t.userId = userId;
        t.idempotencyKey = idempotencyKey;
        t.fromAccountRef = fromAccountRef;
        t.toAccountRef = toAccountRef;
        t.amount = amount;
        t.currency = currency;
        t.status = "PENDING";
        OffsetDateTime now = OffsetDateTime.now();
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    public void applyResult(String status, String reason) {
        this.status = status;
        this.reason = reason;
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFromAccountRef() {
        return fromAccountRef;
    }

    public String getToAccountRef() {
        return toAccountRef;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
