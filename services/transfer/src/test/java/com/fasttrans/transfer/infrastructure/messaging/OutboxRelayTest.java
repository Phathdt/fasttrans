package com.fasttrans.transfer.infrastructure.messaging;

import com.fasttrans.transfer.infrastructure.persistence.OutboxJpaEntity;
import com.fasttrans.transfer.infrastructure.persistence.SpringDataOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock private SpringDataOutboxRepository outboxRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxRepository, kafkaTemplate);
    }

    @Test
    void relay_does_nothing_when_no_pending_rows() {
        when(outboxRepository.lockPendingBatch()).thenReturn(List.of());

        relay.relay();

        verifyNoInteractions(kafkaTemplate);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void relay_publishes_and_marks_sent_on_success() throws Exception {
        OutboxJpaEntity row = OutboxJpaEntity.pending(
                UUID.randomUUID(), UUID.randomUUID(), "transfer.requested", "ACC001", "{\"data\":1}");
        when(outboxRepository.lockPendingBatch()).thenReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(
                mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        relay.relay();

        verify(kafkaTemplate).send("transfer.requested", "ACC001", "{\"data\":1}");
        verify(outboxRepository).save(row);
        // After markSent the entity status should be SENT
        assertThat(row).extracting("status").asString().isEqualTo("SENT");
    }

    @Test
    void relay_skips_row_on_execution_exception_and_continues() throws Exception {
        OutboxJpaEntity failRow = OutboxJpaEntity.pending(
                UUID.randomUUID(), UUID.randomUUID(), "transfer.requested", "ACC001", "{}");
        OutboxJpaEntity okRow = OutboxJpaEntity.pending(
                UUID.randomUUID(), UUID.randomUUID(), "transfer.requested", "ACC002", "{}");

        when(outboxRepository.lockPendingBatch()).thenReturn(List.of(failRow, okRow));

        CompletableFuture<SendResult<String, String>> failFuture = new CompletableFuture<>();
        failFuture.completeExceptionally(new RuntimeException("broker down"));

        CompletableFuture<SendResult<String, String>> okFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));

        when(kafkaTemplate.send(anyString(), eq("ACC001"), anyString())).thenReturn(failFuture);
        when(kafkaTemplate.send(anyString(), eq("ACC002"), anyString())).thenReturn(okFuture);

        relay.relay();

        // failRow must NOT be saved; okRow must be saved
        verify(outboxRepository, never()).save(failRow);
        verify(outboxRepository).save(okRow);
    }

    @Test
    void relay_stops_on_interrupted_exception() throws Exception {
        OutboxJpaEntity row = OutboxJpaEntity.pending(
                UUID.randomUUID(), UUID.randomUUID(), "transfer.requested", "ACC001", "{}");
        when(outboxRepository.lockPendingBatch()).thenReturn(List.of(row));

        // Return a future that throws InterruptedException on .get()
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> interruptedFuture =
                mock(CompletableFuture.class);
        when(interruptedFuture.get()).thenThrow(new InterruptedException("interrupted"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(interruptedFuture);

        relay.relay();

        // Row should not be saved
        verify(outboxRepository, never()).save(any());
        // Thread interrupted flag should be set
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        // Clear interrupt for subsequent tests
        Thread.interrupted();
    }
}
