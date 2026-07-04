package com.fasttrans.account.application.dto;

/**
 * REST response for GET /accounts/{accountRef} — no balance field to protect privacy.
 * Only the owner name and public ref are returned.
 */
public record AccountLookupResponse(
        String accountRef,
        String ownerName
) {
}
