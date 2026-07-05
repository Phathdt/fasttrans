package com.fasttrans.auth.infrastructure.web;

import com.fasttrans.auth.domain.exception.UnauthorizedException;
import com.fasttrans.commons.web.ErrorCode;
import com.fasttrans.commons.web.ErrorResponse;
import com.fasttrans.commons.web.ErrorResponseFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Maps auth domain exceptions to the shared error envelope.
// Framework exceptions (validation, missing header, unexpected) are handled by
// the base handler in fasttrans-web-commons.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorResponseFactory errors;

    public GlobalExceptionHandler(ErrorResponseFactory errors) {
        this.errors = errors;
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return errors.build(ErrorCode.UNAUTHORIZED, ex.getMessage(), ex);
    }
}
