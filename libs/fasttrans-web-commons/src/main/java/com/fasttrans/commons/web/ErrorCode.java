package com.fasttrans.commons.web;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable error codes shared across services. Each constant maps to the
 * HTTP status the handler returns. Services reference only the codes they raise;
 * the library keeps the full set so the envelope contract stays uniform.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MISSING_HEADER(HttpStatus.BAD_REQUEST),
    // Generic framework-level client errors — the HTTP status stays truthful
    // instead of collapsing into a 500 (see GlobalExceptionHandler).
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND),
    OWNERSHIP_DENIED(HttpStatus.FORBIDDEN),
    ACCOUNT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
