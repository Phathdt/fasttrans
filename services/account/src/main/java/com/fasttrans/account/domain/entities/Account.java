package com.fasttrans.account.domain.entities;

import com.fasttrans.account.domain.exception.InsufficientFundsException;

import java.time.Instant;
import java.util.UUID;

/**
 * Account — source of truth for ownership + balance cache.
 * Pure domain POJO: no Spring/JPA/Kafka. Balance is only mutated via debit/credit.
 */
public class Account {

    private UUID id;
    /** Public 12-digit identifier, the only one exposed externally (gRPC, events). */
    private String accountRef;
    /** Logical FK to auth_db.users.id (no physical cross-db FK). */
    private UUID userId;
    private String ownerName;
    /** Balance in minor units (VND: 1 = 1 dong). Cache of the ledger, CHECK >= 0 in the DB. */
    private long balance;
    private String currency;
    private Instant updatedAt;

    public Account() {
    }

    public Account(UUID id, String accountRef, UUID userId, String ownerName,
                   long balance, String currency, Instant updatedAt) {
        this.id = id;
        this.accountRef = accountRef;
        this.userId = userId;
        this.ownerName = ownerName;
        this.balance = balance;
        this.currency = currency;
        this.updatedAt = updatedAt;
    }

    /**
     * Debits the account; raises InsufficientFundsException when the balance is too low.
     * amount must be > 0.
     */
    public void debit(long amount) {
        if (balance < amount) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        this.balance -= amount;
    }

    /** Credits the account. amount must be > 0. */
    public void credit(long amount) {
        this.balance += amount;
    }

    /** True when this account belongs to the given user. */
    public boolean isOwnedBy(UUID candidateUserId) {
        return userId.equals(candidateUserId);
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
