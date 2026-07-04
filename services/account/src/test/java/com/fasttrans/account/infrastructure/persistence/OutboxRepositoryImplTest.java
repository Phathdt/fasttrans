package com.fasttrans.account.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasttrans.account.application.dto.TransferResultEvent;
import com.fasttrans.account.domain.entities.TransferResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRepositoryImplTest {

    @Mock SpringDataOutboxRepository jpa;

    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    OutboxRepositoryImpl repo;

    @BeforeEach
    void setUp() {
        repo = new OutboxRepositoryImpl(jpa, objectMapper);
    }

    @Test
    void enqueue_completedResult_savesValidJsonPayload() throws Exception {
        UUID transferId = UUID.randomUUID();
        TransferResult result = TransferResult.completed(transferId);

        repo.enqueue(result);

        ArgumentCaptor<OutboxJpaEntity> cap = ArgumentCaptor.forClass(OutboxJpaEntity.class);
        verify(jpa).save(cap.capture());

        OutboxJpaEntity saved = cap.getValue();
        assertThat(saved.getTopic()).isEqualTo("transfer.result");
        assertThat(saved.getMsgKey()).isEqualTo(transferId.toString());
        assertThat(saved.getStatus()).isEqualTo("PENDING");

        // Payload must be deserializable as TransferResultEvent
        TransferResultEvent event = objectMapper.readValue(saved.getPayload(), TransferResultEvent.class);
        assertThat(event.transferId()).isEqualTo(transferId);
        assertThat(event.status()).isEqualTo(TransferResult.STATUS_COMPLETED);
        assertThat(event.reason()).isNull();
        assertThat(event.messageId()).isNotNull();
        assertThat(event.processedAt()).isNotNull();
    }

    @Test
    void enqueue_insufficientFundsResult_includesReasonInPayload() throws Exception {
        UUID transferId = UUID.randomUUID();
        TransferResult result = TransferResult.insufficientFunds(transferId);

        repo.enqueue(result);

        ArgumentCaptor<OutboxJpaEntity> cap = ArgumentCaptor.forClass(OutboxJpaEntity.class);
        verify(jpa).save(cap.capture());

        TransferResultEvent event = objectMapper.readValue(cap.getValue().getPayload(), TransferResultEvent.class);
        assertThat(event.status()).isEqualTo(TransferResult.STATUS_FAILED);
        assertThat(event.reason()).isEqualTo(TransferResult.REASON_INSUFFICIENT);
    }

    @Test
    void enqueue_accountNotFoundResult_includesReasonInPayload() throws Exception {
        UUID transferId = UUID.randomUUID();
        TransferResult result = TransferResult.accountNotFound(transferId);

        repo.enqueue(result);

        ArgumentCaptor<OutboxJpaEntity> cap = ArgumentCaptor.forClass(OutboxJpaEntity.class);
        verify(jpa).save(cap.capture());

        TransferResultEvent event = objectMapper.readValue(cap.getValue().getPayload(), TransferResultEvent.class);
        assertThat(event.reason()).isEqualTo(TransferResult.REASON_NOT_FOUND);
    }
}
