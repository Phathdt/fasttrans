package com.fasttrans.account.domain.entities;

import java.util.UUID;

/**
 * Domain result of processing a transfer.requested event.
 * status: COMPLETED | FAILED. reason: null when COMPLETED;
 * INSUFFICIENT_FUNDS | ACCOUNT_NOT_FOUND when FAILED.
 */
public record TransferResult(UUID transferId, String status, String reason) {

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String REASON_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    public static final String REASON_INSUFFICIENT = "INSUFFICIENT_FUNDS";

    public static TransferResult completed(UUID transferId) {
        return new TransferResult(transferId, STATUS_COMPLETED, null);
    }

    public static TransferResult accountNotFound(UUID transferId) {
        return new TransferResult(transferId, STATUS_FAILED, REASON_NOT_FOUND);
    }

    public static TransferResult insufficientFunds(UUID transferId) {
        return new TransferResult(transferId, STATUS_FAILED, REASON_INSUFFICIENT);
    }
}
