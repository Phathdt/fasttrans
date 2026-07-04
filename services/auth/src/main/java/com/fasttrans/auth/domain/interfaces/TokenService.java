package com.fasttrans.auth.domain.interfaces;

/**
 * Domain contract for JWT token issuing/parsing.
 * Signature validation only — session revocation is checked via SessionStore.
 */
public interface TokenService {

    /** Issues a signed token for the given userId. */
    String issue(String userId);

    /** Parses+validates the signature/expiry. Returns userId, or null when invalid. */
    String parseUserId(String token);

    /** Token / session TTL in seconds. */
    long getTtlSeconds();
}
