package com.fasttrans.transfer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataProcessedMessageRepository extends JpaRepository<ProcessedMessageJpaEntity, UUID> {
}
