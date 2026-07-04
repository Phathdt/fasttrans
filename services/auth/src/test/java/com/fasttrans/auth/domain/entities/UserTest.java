package com.fasttrans.auth.domain.entities;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void constructorAndGetters() {
        UUID id = UUID.randomUUID();
        User user = new User(id, "alice", "$2b$hash");

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getPasswordHash()).isEqualTo("$2b$hash");
    }

    @Test
    void noArgsConstructorAndSetters() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setUsername("bob");
        user.setPasswordHash("$2b$other");

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo("bob");
        assertThat(user.getPasswordHash()).isEqualTo("$2b$other");
    }
}
