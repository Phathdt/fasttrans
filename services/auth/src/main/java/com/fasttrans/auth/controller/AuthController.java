package com.fasttrans.auth.controller;

import com.fasttrans.auth.dto.LoginRequest;
import com.fasttrans.auth.dto.LoginResponse;
import com.fasttrans.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

// Traefik strips '/api' → controller mounted at '/auth'.
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req.username(), req.password())
                .map(token -> ResponseEntity.ok(new LoginResponse(token, authService.getTtlSeconds())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    // Endpoint for Traefik ForwardAuth: 200 + X-User-Id if valid, 401 otherwise.
    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearer(authorization);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<String> userId = authService.verify(token);
        return userId
                .map(id -> ResponseEntity.ok().header("X-User-Id", id).<Void>build())
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
