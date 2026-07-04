package com.fasttrans.account.domain.exception;

import java.util.UUID;

/** Raised when an account's balance is lower than the amount to debit. */
public class InsufficientFundsException extends RuntimeException {

    private final UUID accountId;
    private final long balance;
    private final long amount;

    public InsufficientFundsException(UUID accountId, long balance, long amount) {
        super("Insufficient funds accountId=" + accountId + " balance=" + balance + " amount=" + amount);
        this.accountId = accountId;
        this.balance = balance;
        this.amount = amount;
    }

    public UUID getAccountId() { return accountId; }
    public long getBalance() { return balance; }
    public long getAmount() { return amount; }
}
