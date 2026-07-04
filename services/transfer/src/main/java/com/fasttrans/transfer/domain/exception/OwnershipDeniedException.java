package com.fasttrans.transfer.domain.exception;

// fromAccountRef does not belong to the user → 403.
public class OwnershipDeniedException extends RuntimeException {
    public OwnershipDeniedException(String message) {
        super(message);
    }
}
