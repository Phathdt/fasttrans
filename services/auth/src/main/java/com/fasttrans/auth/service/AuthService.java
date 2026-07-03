package com.fasttrans.auth.service;

import com.fasttrans.auth.entity.UserEntity;
import com.fasttrans.auth.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

// Login: validate password (BCrypt) → issue JWT + session. Verify: delegate TokenService.
@Service
public class AuthService {

    private final UserRepository users;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository users, TokenService tokenService) {
        this.users = users;
        this.tokenService = tokenService;
    }

    // Returns token if username+password are correct, otherwise empty (→ 401 in controller).
    public Optional<String> login(String username, String password) {
        Optional<UserEntity> user = users.findByUsername(username);
        if (user.isEmpty() || !encoder.matches(password, user.get().getPasswordHash())) {
            return Optional.empty();
        }
        return Optional.of(tokenService.issue(user.get().getId().toString()));
    }

    public Optional<String> verify(String token) {
        return tokenService.verify(token);
    }

    public long getTtlSeconds() {
        return tokenService.getTtlSeconds();
    }
}
