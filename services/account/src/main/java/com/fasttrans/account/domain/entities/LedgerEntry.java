package com.fasttrans.account.domain.entities;

import java.time.Instant;
import java.util.UUID;

/**
 * Double-entry ledger — source of truth for balances.
 * DEBIT = money leaving the account, CREDIT = money entering the account.
 * balanceAfter = snapshot of the balance after the entry (audit trail).
 * Pure domain POJO.
 */
public class LedgerEntry {

    public static final String DIR_DEBIT = "DEBIT";
    public static final String DIR_CREDIT = "CREDIT";

    private UUID id;
    private UUID accountId;
    private UUID transferId;
    /** DEBIT or CREDIT. */
    private String direction;
    /** Amount in minor units, always > 0. */
    private long amount;
    /** Snapshot of the balance after applying this entry. */
    private long balanceAfter;
    private Instant createdAt;

    public LedgerEntry() {
    }

    public LedgerEntry(UUID id, UUID accountId, UUID transferId, String direction,
                       long amount, long balanceAfter, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.transferId = transferId;
        this.direction = direction;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    /** Factory for a DEBIT entry. */
    public static LedgerEntry debit(UUID accountId, UUID transferId, long amount, long balanceAfter) {
        return new LedgerEntry(UUID.randomUUID(), accountId, transferId, DIR_DEBIT, amount, balanceAfter, null);
    }

    /** Factory for a CREDIT entry. */
    public static LedgerEntry credit(UUID accountId, UUID transferId, long amount, long balanceAfter) {
        return new LedgerEntry(UUID.randomUUID(), accountId, transferId, DIR_CREDIT, amount, balanceAfter, null);
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
