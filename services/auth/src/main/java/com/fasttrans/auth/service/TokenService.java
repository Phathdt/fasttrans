package com.fasttrans.auth.service;

import com.fasttrans.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

// Create/verify JWT + manage Redis session. Verify = valid signature AND key still in Redis (revocable).
@Service
public class TokenService {

    private static final String SESSION_PREFIX = "session:";

    private final JwtProperties props;
    private final StringRedisTemplate redis;
    private final SecretKey key;

    public TokenService(JwtProperties props, StringRedisTemplate redis) {
        this.props = props;
        this.redis = redis;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    // Create JWT (sub=userId, exp=now+ttl) and store Redis session with the same TTL.
    public String issue(String userId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getTtlSeconds());

        String token = Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();

        redis.opsForValue().set(SESSION_PREFIX + token, userId, Duration.ofSeconds(props.getTtlSeconds()));
        return token;
    }

    // Returns userId if token is valid (signature OK + session still in Redis), otherwise empty.
    public Optional<String> verify(String token) {
        String userId;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            userId = claims.getSubject();
        } catch (Exception e) {
            return Optional.empty(); // invalid signature / expired / malformed
        }

        String stored = redis.opsForValue().get(SESSION_PREFIX + token);
        if (stored == null || !stored.equals(userId)) {
            return Optional.empty(); // revoked or session TTL expired
        }
        return Optional.of(userId);
    }

    public long getTtlSeconds() {
        return props.getTtlSeconds();
    }
}
