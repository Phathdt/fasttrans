package com.fasttrans.transfer.application.dto;

// Returned by POST /transfers — the newly created (or idempotently replayed) transfer's id + status.
public record CreateTransferResponse(
        String id,
        String status
) {
}
