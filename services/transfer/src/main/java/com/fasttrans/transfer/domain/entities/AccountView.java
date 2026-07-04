package com.fasttrans.transfer.domain.entities;

/**
 * Read-only view of an account owned by the current user, returned by the account service.
 * Domain-level projection so AccountClient does not leak gRPC types.
 */
public record AccountView(
        String accountRef,
        String ownerName,
        long balance,
        String currency
) {}
