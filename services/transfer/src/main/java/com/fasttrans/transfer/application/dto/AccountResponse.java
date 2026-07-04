package com.fasttrans.transfer.application.dto;

// Returned for GET /accounts (mapped from the gRPC ListAccounts view).
public record AccountResponse(
        String accountRef,
        String ownerName,
        long balance,
        String currency
) {
}
