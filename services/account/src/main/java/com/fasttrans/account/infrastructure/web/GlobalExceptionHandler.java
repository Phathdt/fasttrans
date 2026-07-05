package com.fasttrans.account.infrastructure.web;

import com.fasttrans.account.domain.exception.AccountNotFoundException;
import com.fasttrans.commons.web.ErrorCode;
import com.fasttrans.commons.web.ErrorResponse;
import com.fasttrans.commons.web.ErrorResponseFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Maps account domain exceptions to the shared error envelope.
// Framework exceptions (missing header, validation, unexpected) are handled by
// the base handler in fasttrans-web-commons.
// InsufficientFundsException is intentionally NOT mapped here — it is caught
// inside ProcessTransferService (Kafka async flow) and never surfaces over REST.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorResponseFactory errors;

    public GlobalExceptionHandler(ErrorResponseFactory errors) {
        this.errors = errors;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        return errors.build(ErrorCode.ACCOUNT_NOT_FOUND, ex.getMessage(), ex);
    }
}
