package com.fasttrans.auth.dto;

public record LoginResponse(String token, long expiresIn) {
}
