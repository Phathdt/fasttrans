package com.fasttrans.transfer.infrastructure.web;

import com.fasttrans.commons.web.ErrorCode;
import com.fasttrans.commons.web.ErrorResponse;
import com.fasttrans.commons.web.ErrorResponseFactory;
import com.fasttrans.transfer.domain.exception.AccountUnavailableException;
import com.fasttrans.transfer.domain.exception.OwnershipDeniedException;
import com.fasttrans.transfer.domain.exception.TransferNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Maps transfer domain exceptions to the shared error envelope.
// Framework exceptions (validation, missing header, unexpected) are handled by
// the base handler in fasttrans-web-commons.
@RestControllerAdvice
public class TransferExceptionHandler {

    private final ErrorResponseFactory errors;

    public TransferExceptionHandler(ErrorResponseFactory errors) {
        this.errors = errors;
    }

    @ExceptionHandler(OwnershipDeniedException.class)
    public ResponseEntity<ErrorResponse> handleOwnershipDenied(OwnershipDeniedException ex) {
        return errors.build(ErrorCode.OWNERSHIP_DENIED, ex.getMessage(), ex);
    }

    @ExceptionHandler(AccountUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAccountUnavailable(AccountUnavailableException ex) {
        // Generic message — do not leak the underlying gRPC failure.
        return errors.build(ErrorCode.ACCOUNT_UNAVAILABLE,
                "Account service is temporarily unavailable. Please try again later.", ex);
    }

    @ExceptionHandler(TransferNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransferNotFound(TransferNotFoundException ex) {
        return errors.build(ErrorCode.TRANSFER_NOT_FOUND, ex.getMessage(), ex);
    }
}
