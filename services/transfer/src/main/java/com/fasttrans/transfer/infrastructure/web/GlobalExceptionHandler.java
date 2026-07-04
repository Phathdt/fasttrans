package com.fasttrans.transfer.infrastructure.web;

import com.fasttrans.transfer.domain.exception.AccountUnavailableException;
import com.fasttrans.transfer.domain.exception.OwnershipDeniedException;
import com.fasttrans.transfer.domain.exception.TransferNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Centralized error handling; returns a ProblemDetail (RFC 7807) with the corresponding HTTP status.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OwnershipDeniedException.class)
    public ProblemDetail handleOwnershipDenied(OwnershipDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(AccountUnavailableException.class)
    public ProblemDetail handleAccountUnavailable(AccountUnavailableException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        pd.setDetail("Account service is temporarily unavailable. Please try again later.");
        return pd;
    }

    @ExceptionHandler(TransferNotFoundException.class)
    public ProblemDetail handleTransferNotFound(TransferNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setDetail(ex.getMessage());
        return pd;
    }

    // Missing X-User-Id or Idempotency-Key header → 400
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail("Required header is missing: " + ex.getHeaderName());
        return pd;
    }

    // @Valid validation failed → 400 with the list of field errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        pd.setDetail(errors);
        return pd;
    }
}
