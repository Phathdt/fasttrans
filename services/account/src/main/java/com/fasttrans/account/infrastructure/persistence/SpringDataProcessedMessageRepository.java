package com.fasttrans.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataProcessedMessageRepository extends JpaRepository<ProcessedMessageJpaEntity, UUID> {
    // existsById(messageId) is enough for the inbox dedup check.
}
