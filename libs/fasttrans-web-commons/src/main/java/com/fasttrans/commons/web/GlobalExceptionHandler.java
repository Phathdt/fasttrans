package com.fasttrans.commons.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Base handler for framework-level exceptions, shared by all services. Produces
 * the {@link ErrorResponse} envelope with the truthful HTTP status.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring's built-in MVC
 * exceptions (malformed JSON, wrong HTTP method, unknown route, path-variable
 * type mismatch, ...) keep their correct 4xx status instead of being swallowed
 * by the {@code Exception} catch-all and reported as 500. Each of those is
 * re-emitted as our envelope via {@link #handleExceptionInternal}.
 *
 * <p>Ordered {@code LOWEST_PRECEDENCE} so a per-service domain
 * {@code @RestControllerAdvice} (which maps OwnershipDenied, AccountNotFound,
 * ...) always wins for the exceptions it declares; this advice is the last
 * resort. Domain handlers inject {@link ErrorResponseFactory} rather than
 * extending this class, to avoid duplicate advice beans handling the same type.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final ErrorResponseFactory errors;

    public GlobalExceptionHandler(ErrorResponseFactory errors) {
        this.errors = errors;
    }

    /** Missing X-User-Id / Idempotency-Key header → 400. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return errors.build(ErrorCode.MISSING_HEADER,
                "Required header is missing: " + ex.getHeaderName(), ex);
    }

    /** Anything unexpected → 500 with a generic message (internal detail hidden). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return errors.build(ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Please try again later.", ex);
    }

    /**
     * Re-emit every framework exception handled by {@link ResponseEntityExceptionHandler}
     * as our envelope, keeping the status Spring already resolved. {@code @Valid}
     * body validation carries per-field details; the rest map by status.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        HttpStatus status = HttpStatus.resolve(statusCode.value());
        ErrorCode code = codeFor(status);

        List<FieldError> details = null;
        String message = messageFor(status);
        if (ex instanceof MethodArgumentNotValidException manv) {
            code = ErrorCode.VALIDATION_FAILED;
            message = "Validation failed";
            details = manv.getBindingResult().getFieldErrors().stream()
                    .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                    .toList();
        }

        ErrorResponse envelope = errors.body(code, message, details, ex);
        return ResponseEntity.status(statusCode).headers(headers).body(envelope);
    }

    private ErrorCode codeFor(HttpStatus status) {
        if (status == null) {
            return ErrorCode.INTERNAL_ERROR;
        }
        return switch (status) {
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? ErrorCode.BAD_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
    }

    private String messageFor(HttpStatus status) {
        if (status != null && status.is4xxClientError()) {
            return status.getReasonPhrase();
        }
        return "An unexpected error occurred. Please try again later.";
    }
}
