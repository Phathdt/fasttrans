package com.fasttrans.auth.application.services;

import com.fasttrans.auth.domain.entities.User;
import com.fasttrans.auth.domain.interfaces.SessionStore;
import com.fasttrans.auth.domain.interfaces.TokenService;
import com.fasttrans.auth.domain.interfaces.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Login: validate password (BCrypt) → issue JWT + store session.
 * Verify: parse token signature AND confirm the session is still active (I7 revocable).
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final TokenService tokenService;
    private final SessionStore sessionStore;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository users, TokenService tokenService, SessionStore sessionStore) {
        this.users = users;
        this.tokenService = tokenService;
        this.sessionStore = sessionStore;
    }

    // Returns token if username+password are correct, otherwise empty (→ 401 in controller).
    public Optional<String> login(String username, String password) {
        Optional<User> user = users.findByUsername(username);
        if (user.isEmpty() || !encoder.matches(password, user.get().getPasswordHash())) {
            return Optional.empty();
        }
        String userId = user.get().getId().toString();
        String token = tokenService.issue(userId);
        sessionStore.store(token, userId, tokenService.getTtlSeconds());
        return Optional.of(token);
    }

    // Returns userId if the token signature is valid AND the session is still active.
    public Optional<String> verify(String token) {
        String userId = tokenService.parseUserId(token);
        if (userId == null || !sessionStore.isActive(token, userId)) {
            return Optional.empty();
        }
        return Optional.of(userId);
    }

    public long getTtlSeconds() {
        return tokenService.getTtlSeconds();
    }
}
