package com.fasttrans.auth.infrastructure.web;

import com.fasttrans.auth.application.dto.LoginRequest;
import com.fasttrans.auth.application.dto.LoginResponse;
import com.fasttrans.auth.application.services.AuthService;
import com.fasttrans.auth.domain.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "auth", description = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Log in with username/password and receive a bearer token")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        // Invalid credentials → UnauthorizedException → 401 error envelope.
        // Success returns the raw DTO; SuccessEnvelopeAdvice wraps it as { data, meta }.
        return authService.login(req.username(), req.password())
                .map(token -> new LoginResponse(token, authService.getTtlSeconds()))
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));
    }

    // Endpoint for Traefik ForwardAuth: 200 + X-User-Id if valid, 401 otherwise.
    // Hidden from public OpenAPI spec — internal gateway use only.
    @Hidden
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
