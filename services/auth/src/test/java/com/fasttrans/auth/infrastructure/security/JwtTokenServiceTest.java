package com.fasttrans.auth.infrastructure.security;

import com.fasttrans.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    // Must be >=32 bytes for HMAC-SHA256
    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes!!";
    private static final String OTHER_SECRET = "other-secret-key-at-least-32-bytes-!!";

    JwtTokenService tokenService;
    JwtProperties props;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.setSecret(SECRET);
        props.setTtlSeconds(3600);
        tokenService = new JwtTokenService(props);
    }

    @Test
    void issue_then_parseUserId_roundTrip() {
        String token = tokenService.issue("user-123");

        assertThat(tokenService.parseUserId(token)).isEqualTo("user-123");
    }

    @Test
    void parseUserId_badSignature_returnsNull() {
        // Sign with a different key
        SecretKey otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithOtherKey = Jwts.builder()
                .subject("user-123")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKey)
                .compact();

        assertThat(tokenService.parseUserId(tokenWithOtherKey)).isNull();
    }

    @Test
    void parseUserId_expiredToken_returnsNull() {
        // Issue a token that is already expired (ttl = -1 second)
        props.setTtlSeconds(-1);
        JwtTokenService expiredService = new JwtTokenService(props);
        String expiredToken = expiredService.issue("user-123");

        // Parse with normal service — signature is valid but token expired
        assertThat(tokenService.parseUserId(expiredToken)).isNull();
    }

    @Test
    void parseUserId_garbageString_returnsNull() {
        assertThat(tokenService.parseUserId("not.a.jwt")).isNull();
        assertThat(tokenService.parseUserId("garbage")).isNull();
        assertThat(tokenService.parseUserId("")).isNull();
    }

    @Test
    void getTtlSeconds_returnsConfiguredValue() {
        assertThat(tokenService.getTtlSeconds()).isEqualTo(3600L);
    }
}
