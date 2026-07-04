package com.fasttrans.transfer.domain.exception;

// gRPC account service did not respond (deadline/unavailable) → 503.
public class AccountUnavailableException extends RuntimeException {
    public AccountUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
