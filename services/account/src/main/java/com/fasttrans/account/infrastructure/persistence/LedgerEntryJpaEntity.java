package com.fasttrans.account.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Double-entry ledger — source of truth for balances.
 * DEBIT = money leaving the account, CREDIT = money entering the account.
 * balance_after = snapshot of the balance after the entry (audit trail).
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntryJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** Internal FK UUID of accounts.id. */
    @Column(name = "account_id", nullable = false, columnDefinition = "uuid")
    private UUID accountId;

    @Column(name = "transfer_id", nullable = false, columnDefinition = "uuid")
    private UUID transferId;

    /** DEBIT or CREDIT. */
    @Column(nullable = false, length = 6)
    private String direction;

    /** Amount in minor units, always > 0. */
    @Column(nullable = false)
    private long amount;

    /** Snapshot of the balance after applying this entry. */
    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    // --- getters / setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public UUID getTransferId() { return transferId; }
    public void setTransferId(UUID transferId) { this.transferId = transferId; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public long getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(long balanceAfter) { this.balanceAfter = balanceAfter; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
