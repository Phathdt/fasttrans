package com.fasttrans.account.application.dto;

/**
 * REST response for GET /accounts — includes balance visible to the account owner.
 * Balance is in minor units (VND: 1 = 1 dong).
 */
public record AccountResponse(
        String accountRef,
        String ownerName,
        long balance,
        String currency
) {
}
