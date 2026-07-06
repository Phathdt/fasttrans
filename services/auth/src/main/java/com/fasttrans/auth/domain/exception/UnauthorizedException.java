package com.fasttrans.auth.domain.exception;

/**
 * Thrown when login credentials are invalid. Maps to HTTP 401 / UNAUTHORIZED
 * via the auth GlobalExceptionHandler.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
