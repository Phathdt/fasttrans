package com.fasttrans.account.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * accounts table — source of truth for ownership + balance cache.
 * balance is only updated in the same transaction as ledger_entries.
 */
@Entity
@Table(name = "accounts")
public class AccountJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** Public 12-digit identifier, the only one exposed externally (gRPC, events). */
    @Column(name = "account_ref", nullable = false, unique = true)
    private String accountRef;

    /** Logical FK to auth_db.users.id (no physical cross-db FK). */
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    /** Balance in minor units (VND: 1 = 1 dong). Cache of the ledger, CHECK >= 0 in the DB. */
    @Column(nullable = false)
    private long balance;

    @Column(nullable = false)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    // --- getters / setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getAccountRef() { return accountRef; }
    public void setAccountRef(String accountRef) { this.accountRef = accountRef; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
