package com.fasttrans.account.repository;

import com.fasttrans.account.entity.ProcessedMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessageEntity, UUID> {
    // existsById(messageId) is enough for the inbox dedup check.
}
