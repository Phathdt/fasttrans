package com.fasttrans.account.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasttrans.account.application.dto.TransferResultEvent;
import com.fasttrans.account.domain.entities.TransferResult;
import com.fasttrans.account.domain.interfaces.OutboxRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA-backed implementation of the OutboxRepository domain contract.
 * Serializes the domain TransferResult to the TransferResultEvent JSON payload
 * (contract matches transfer-events.md) before persisting to the outbox table.
 */
@Repository
public class OutboxRepositoryImpl implements OutboxRepository {

    private static final String TOPIC_RESULT = "transfer.result";

    private final SpringDataOutboxRepository jpa;
    private final ObjectMapper objectMapper;

    public OutboxRepositoryImpl(SpringDataOutboxRepository jpa, ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueue(TransferResult result) {
        TransferResultEvent event = new TransferResultEvent(
                UUID.randomUUID(),
                result.transferId(),
                result.status(),
                result.reason(),
                Instant.now()
        );
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize TransferResultEvent", e);
        }
        OutboxJpaEntity outbox = OutboxJpaEntity.pending(
                result.transferId(),
                TOPIC_RESULT,
                result.transferId().toString(),  // msg_key = transferId (partition key)
                payload
        );
        jpa.save(outbox);
    }
}
