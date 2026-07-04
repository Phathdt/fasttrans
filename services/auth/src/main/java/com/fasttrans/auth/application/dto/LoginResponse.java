package com.fasttrans.auth.application.dto;

public record LoginResponse(String token, long expiresIn) {
}
