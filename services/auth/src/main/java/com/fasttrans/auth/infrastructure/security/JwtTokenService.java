package com.fasttrans.auth.infrastructure.security;

import com.fasttrans.auth.domain.interfaces.TokenService;
import com.fasttrans.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT issuing/parsing (HMAC). Signature + expiry validation only;
 * session revocation is enforced separately via SessionStore.
 */
@Component
public class JwtTokenService implements TokenService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtTokenService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    // Create JWT (sub=userId, exp=now+ttl).
    @Override
    public String issue(String userId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getTtlSeconds());

        return Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    // Returns userId if signature valid + not expired, otherwise null.
    @Override
    public String parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            return null; // invalid signature / expired / malformed
        }
    }

    @Override
    public long getTtlSeconds() {
        return props.getTtlSeconds();
    }
}
