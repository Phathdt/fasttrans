package com.fasttrans.account.domain.exception;

/**
 * Thrown when an account lookup by accountRef returns no result.
 * Maps to HTTP 404 via GlobalExceptionHandler.
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String message) {
        super(message);
    }
}
