package com.fasttrans.auth.infrastructure.persistence;

import com.fasttrans.auth.domain.entities.User;
import com.fasttrans.auth.domain.interfaces.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** JPA-backed implementation of the UserRepository domain contract. */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final SpringDataUserRepository jpa;
    private final UserMapper mapper;

    public UserRepositoryImpl(SpringDataUserRepository jpa, UserMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username).map(mapper::toDomain);
    }
}
