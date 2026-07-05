package com.fasttrans.commons.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Error payload inside {@link ErrorResponse#error()}.
 *
 * @param code       machine-readable error code (from {@link ErrorCode})
 * @param message    human-readable message (specific for 4xx, generic for 5xx)
 * @param details    validation failures; omitted from JSON unless non-empty
 * @param stackTrace populated only when the stacktrace flag is enabled; omitted when null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> details,
        String stackTrace) {
}
