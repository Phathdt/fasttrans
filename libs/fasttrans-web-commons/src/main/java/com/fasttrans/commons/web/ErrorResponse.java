package com.fasttrans.commons.web;

/**
 * Error envelope: every 4xx/5xx REST body is
 * {@code { "error": { ... }, "meta": { ... } }}. The HTTP status stays truthful;
 * the envelope is only the body.
 *
 * @param error the error payload
 * @param meta  request metadata (requestId, timestamp)
 */
public record ErrorResponse(ErrorBody error, Meta meta) {
}
