package com.fasttrans.auth.domain.entities;

import java.util.UUID;

/**
 * User — pre-seeded credential holder. Pure domain POJO (no Spring/JPA).
 * Does NOT hold account_id (1 user N accounts, ownership managed by the account service).
 */
public class User {

    private UUID id;
    private String username;
    private String passwordHash;

    public User() {
    }

    public User(UUID id, String username, String passwordHash) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    // --- getters / setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
