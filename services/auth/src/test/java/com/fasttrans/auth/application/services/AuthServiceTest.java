package com.fasttrans.auth.application.services;

import com.fasttrans.auth.domain.entities.User;
import com.fasttrans.auth.domain.interfaces.SessionStore;
import com.fasttrans.auth.domain.interfaces.TokenService;
import com.fasttrans.auth.domain.interfaces.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock TokenService tokenService;
    @Mock SessionStore sessionStore;

    AuthService authService;

    // Real BCrypt hash of "password" — matches what AuthService internally uses.
    private static final String BCRYPT_PASSWORD =
            new BCryptPasswordEncoder().encode("password");

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, tokenService, sessionStore);
    }

    // --- login ---

    @Test
    void login_correctPassword_returnsToken() {
        User user = new User(USER_ID, "alice", BCRYPT_PASSWORD);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(tokenService.issue(USER_ID.toString())).thenReturn("jwt-token");
        when(tokenService.getTtlSeconds()).thenReturn(3600L);

        Optional<String> result = authService.login("alice", "password");

        assertThat(result).isPresent().contains("jwt-token");
        verify(sessionStore).store("jwt-token", USER_ID.toString(), 3600L);
    }

    @Test
    void login_wrongPassword_returnsEmpty() {
        User user = new User(USER_ID, "alice", BCRYPT_PASSWORD);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Optional<String> result = authService.login("alice", "wrong");

        assertThat(result).isEmpty();
        verify(tokenService, never()).issue(any());
        verify(sessionStore, never()).store(any(), any(), anyLong());
    }

    @Test
    void login_unknownUser_returnsEmpty() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Optional<String> result = authService.login("ghost", "password");

        assertThat(result).isEmpty();
        verify(tokenService, never()).issue(any());
    }

    // --- verify ---

    @Test
    void verify_validTokenAndActiveSession_returnsUserId() {
        when(tokenService.parseUserId("jwt-token")).thenReturn(USER_ID.toString());
        when(sessionStore.isActive("jwt-token", USER_ID.toString())).thenReturn(true);

        Optional<String> result = authService.verify("jwt-token");

        assertThat(result).isPresent().contains(USER_ID.toString());
    }

    @Test
    void verify_invalidToken_returnsEmpty() {
        when(tokenService.parseUserId("bad-token")).thenReturn(null);

        Optional<String> result = authService.verify("bad-token");

        assertThat(result).isEmpty();
        verify(sessionStore, never()).isActive(any(), any());
    }

    @Test
    void verify_expiredSession_returnsEmpty() {
        when(tokenService.parseUserId("jwt-token")).thenReturn(USER_ID.toString());
        when(sessionStore.isActive("jwt-token", USER_ID.toString())).thenReturn(false);

        Optional<String> result = authService.verify("jwt-token");

        assertThat(result).isEmpty();
    }

    // --- getTtlSeconds ---

    @Test
    void getTtlSeconds_delegatesToTokenService() {
        when(tokenService.getTtlSeconds()).thenReturn(86400L);

        assertThat(authService.getTtlSeconds()).isEqualTo(86400L);
    }
}
