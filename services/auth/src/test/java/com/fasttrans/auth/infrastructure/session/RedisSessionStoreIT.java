package com.fasttrans.auth.infrastructure.session;

import com.fasttrans.auth.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for RedisSessionStore.
 * Uses real Postgres (Flyway) + real Redis via Testcontainers.
 * Extends AbstractPostgresIT for datasource wiring; wires Redis inline
 * (Java single-inheritance prevents also extending AbstractRedisIT).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class RedisSessionStoreIT extends AbstractPostgresIT {

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    RedisSessionStore sessionStore;

    @Test
    void store_and_isActive_returnsTrue() {
        sessionStore.store("token-1", "user-1", 60);

        assertThat(sessionStore.isActive("token-1", "user-1")).isTrue();
    }

    @Test
    void isActive_wrongUserId_returnsFalse() {
        sessionStore.store("token-2", "user-2", 60);

        assertThat(sessionStore.isActive("token-2", "wrong-user")).isFalse();
    }

    @Test
    void isActive_nonExistentToken_returnsFalse() {
        assertThat(sessionStore.isActive("nonexistent-token", "any-user")).isFalse();
    }

    @Test
    void store_withExpiredTtl_sessionUnavailable() throws InterruptedException {
        // Store with 1-second TTL, then wait for expiry
        sessionStore.store("token-expire", "user-expire", 1);
        assertThat(sessionStore.isActive("token-expire", "user-expire")).isTrue();

        Thread.sleep(1500);

        assertThat(sessionStore.isActive("token-expire", "user-expire")).isFalse();
    }
}
