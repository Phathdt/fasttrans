package com.fasttrans.commons.web;

/**
 * Success envelope: every 2xx REST body is wrapped as
 * {@code { "data": <payload>, "meta": { ... } }}.
 * Applied automatically by {@link SuccessEnvelopeAdvice}; controllers keep
 * returning their raw DTOs.
 *
 * @param data the original controller payload
 * @param meta request metadata (requestId, timestamp)
 * @param <T>  payload type
 */
public record ApiResponse<T>(T data, Meta meta) {

    public static <T> ApiResponse<T> of(T data, Meta meta) {
        return new ApiResponse<>(data, meta);
    }
}
