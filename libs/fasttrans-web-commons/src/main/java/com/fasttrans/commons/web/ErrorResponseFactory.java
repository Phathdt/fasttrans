package com.fasttrans.commons.web;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds {@link ErrorResponse} envelopes with consistent shape, meta, and
 * stacktrace-flag behaviour. Shared by the base {@link GlobalExceptionHandler}
 * and each service's domain {@code @RestControllerAdvice} so error responses
 * stay uniform without inheritance (which would create duplicate advice beans).
 */
@Component
public class ErrorResponseFactory {

    private final TraceContext traceContext;

    public ErrorResponseFactory(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    /** Simple error (no field details). */
    public ResponseEntity<ErrorResponse> build(ErrorCode code, String message, Throwable ex) {
        return build(code, message, null, ex);
    }

    /** Error with optional per-field validation details. */
    public ResponseEntity<ErrorResponse> build(
            ErrorCode code, String message, List<FieldError> details, Throwable ex) {
        return ResponseEntity.status(code.status()).body(body(code, message, details, ex));
    }

    /**
     * Build just the envelope body (no status). Used when the HTTP status is
     * dictated elsewhere — e.g. the framework exception handlers, which keep the
     * status Spring already resolved and only swap in our envelope.
     */
    public ErrorResponse body(ErrorCode code, String message, List<FieldError> details, Throwable ex) {
        ErrorBody body = new ErrorBody(code.name(), message, details, traceContext.stackTraceOf(ex));
        return new ErrorResponse(body, traceContext.meta());
    }
}
