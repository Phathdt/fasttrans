package com.fasttrans.transfer.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasttrans.transfer.application.dto.TransferRequestedEvent;
import com.fasttrans.transfer.domain.entities.Transfer;
import com.fasttrans.transfer.domain.interfaces.OutboxRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA-backed implementation of the OutboxRepository domain contract.
 * Serializes the transfer.requested event JSON (contract matches transfer-events.md)
 * before persisting to the outbox table. Partition key = fromAccountRef.
 */
@Repository
public class OutboxRepositoryImpl implements OutboxRepository {

    private static final String TOPIC_REQUESTED = "transfer.requested";

    private final SpringDataOutboxRepository jpa;
    private final ObjectMapper objectMapper;

    public OutboxRepositoryImpl(SpringDataOutboxRepository jpa, ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueueTransferRequested(Transfer transfer) {
        UUID messageId = UUID.randomUUID();
        TransferRequestedEvent event = new TransferRequestedEvent(
                messageId.toString(),
                transfer.getId().toString(),
                transfer.getFromAccountRef(),
                transfer.getToAccountRef(),
                transfer.getAmount(),
                transfer.getCurrency(),
                Instant.now().toString()
        );
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to serialize TransferRequestedEvent", e);
        }

        OutboxJpaEntity outbox = OutboxJpaEntity.pending(
                UUID.randomUUID(), transfer.getId(),
                TOPIC_REQUESTED, transfer.getFromAccountRef(), payload);
        jpa.save(outbox);
    }
}
