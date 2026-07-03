package com.fasttrans.transfer.dto;

// Returned for GET /accounts (mapped from gRPC ListAccounts).
public record AccountResponse(
        String accountRef,
        String ownerName,
        long balance,
        String currency
) {
}
