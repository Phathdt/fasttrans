package com.fasttrans.auth.infrastructure.persistence;

import com.fasttrans.auth.domain.entities.User;
import com.fasttrans.auth.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for UserRepositoryImpl.
 * Uses a real Postgres container via AbstractPostgresIT; Flyway runs seed migrations.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserRepositoryImpl.class, UserMapperImpl.class})
@ActiveProfiles("test")
class UserRepositoryImplIT extends AbstractPostgresIT {

    @Autowired
    UserRepositoryImpl userRepository;

    @Test
    void findByUsername_alice_returnsUser() {
        Optional<User> result = userRepository.findByUsername("alice");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("alice");
        assertThat(result.get().getId()).isNotNull();
        assertThat(result.get().getPasswordHash()).isNotBlank();
    }

    @Test
    void findByUsername_bob_returnsUser() {
        Optional<User> result = userRepository.findByUsername("bob");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("bob");
    }

    @Test
    void findByUsername_unknown_returnsEmpty() {
        Optional<User> result = userRepository.findByUsername("nobody");

        assertThat(result).isEmpty();
    }
}
