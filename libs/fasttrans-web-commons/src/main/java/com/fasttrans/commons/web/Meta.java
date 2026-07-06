package com.fasttrans.commons.web;

/**
 * Response metadata present on both success and error envelopes.
 *
 * @param requestId correlation id — equals the current traceId (fallback UUID)
 * @param timestamp ISO-8601 UTC instant the response was built
 */
public record Meta(String requestId, String timestamp) {
}
