package com.fasttrans.transfer.repository;

import com.fasttrans.transfer.entity.ProcessedMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessageEntity, UUID> {
}
