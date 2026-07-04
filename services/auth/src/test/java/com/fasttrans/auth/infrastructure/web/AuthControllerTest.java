package com.fasttrans.auth.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasttrans.auth.application.services.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;

    // --- POST /auth/login ---

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        when(authService.login("alice", "password")).thenReturn(Optional.of("jwt-token"));
        when(authService.getTtlSeconds()).thenReturn(3600L);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void login_wrongCredentials_returns401() throws Exception {
        when(authService.login(anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- GET /auth/verify ---

    @Test
    void verify_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/auth/verify"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_authorizationHeaderNotBearer_returns401() throws Exception {
        mockMvc.perform(get("/auth/verify")
                        .header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_bearerWithEmptyToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/verify")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_validBearerToken_returns200WithXUserId() throws Exception {
        when(authService.verify("jwt-token")).thenReturn(Optional.of("user-id-123"));

        mockMvc.perform(get("/auth/verify")
                        .header("Authorization", "Bearer jwt-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-User-Id", "user-id-123"));
    }

    @Test
    void verify_invalidBearerToken_returns401() throws Exception {
        when(authService.verify("bad-token")).thenReturn(Optional.empty());

        mockMvc.perform(get("/auth/verify")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }
}
