package com.fasttrans.auth.infrastructure.session;

import com.fasttrans.auth.domain.interfaces.SessionStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed revocable session store.
 * key = session:&lt;token&gt;, value = userId, with a TTL matching the token.
 */
@Component
public class RedisSessionStore implements SessionStore {

    private static final String SESSION_PREFIX = "session:";

    private final StringRedisTemplate redis;

    public RedisSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void store(String token, String userId, long ttlSeconds) {
        redis.opsForValue().set(SESSION_PREFIX + token, userId, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public boolean isActive(String token, String userId) {
        String stored = redis.opsForValue().get(SESSION_PREFIX + token);
        return stored != null && stored.equals(userId);
    }
}
