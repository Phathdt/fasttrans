package com.fasttrans.auth.domain.interfaces;

import com.fasttrans.auth.domain.entities.User;

import java.util.Optional;

/** Domain contract for user lookup. Implemented in infrastructure. */
public interface UserRepository {

    Optional<User> findByUsername(String username);
}
