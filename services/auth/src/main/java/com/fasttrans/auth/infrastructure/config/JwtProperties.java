package com.fasttrans.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// JWT configuration from application.yml (jwt.secret, jwt.ttl-seconds).
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    // HMAC secret; demo uses env, DO NOT use in production.
    private String secret;

    // Token TTL + Redis session (seconds). Fixed in Validation Session 1: 24h = 86400s.
    private long ttlSeconds = 86400;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
