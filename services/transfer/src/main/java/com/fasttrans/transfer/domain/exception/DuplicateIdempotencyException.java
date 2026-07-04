package com.fasttrans.transfer.domain.exception;

/**
 * Raised by TransferRepository.save when a concurrent insert already created a transfer
 * with the same (userId, idempotencyKey). The application catches this and re-reads.
 * Wraps the persistence-level unique violation so the domain contract stays framework-free.
 */
public class DuplicateIdempotencyException extends RuntimeException {
    public DuplicateIdempotencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
