package com.fasttrans.auth.domain.interfaces;

/**
 * Domain contract for revocable sessions.
 * A token is valid only while its session is still stored (I7 revocability).
 */
public interface SessionStore {

    /** Stores a session (token → userId) with the given TTL in seconds. */
    void store(String token, String userId, long ttlSeconds);

    /** True when the session exists and maps to the given userId. */
    boolean isActive(String token, String userId);
}
