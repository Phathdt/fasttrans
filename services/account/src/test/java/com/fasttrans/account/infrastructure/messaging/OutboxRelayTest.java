package com.fasttrans.account.infrastructure.messaging;

import com.fasttrans.account.infrastructure.persistence.OutboxJpaEntity;
import com.fasttrans.account.infrastructure.persistence.SpringDataOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock SpringDataOutboxRepository outboxRepository;
    @Mock KafkaTemplate<String, String> kafkaTemplate;

    OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxRepository, kafkaTemplate);
    }

    private OutboxJpaEntity pendingEntry(String topic, String key, String payload) {
        return OutboxJpaEntity.pending(UUID.randomUUID(), topic, key, payload);
    }

    @Test
    void relay_noPendingMessages_doesNothing() {
        when(outboxRepository.lockPendingBatch()).thenReturn(List.of());

        relay.relay();

        verifyNoInteractions(kafkaTemplate);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void relay_pendingMessage_sendsToKafkaAndMarksSent() throws Exception {
        OutboxJpaEntity entry = pendingEntry("transfer.result", "key1", "{\"status\":\"COMPLETED\"}");
        when(outboxRepository.lockPendingBatch()).thenReturn(List.of(entry));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.relay();

        verify(kafkaTemplate).send("transfer.result", "key1", "{\"status\":\"COMPLETED\"}");
        assertThat(entry.getStatus()).isEqualTo("SENT");
        assertThat(entry.getSentAt()).isNotNull();
        verify(outboxRepository).save(entry);
    }

    @Test
    void relay_kafkaSendFails_entryRemainsNotSent() throws Exception {
        OutboxJpaEntity entry = pendingEntry("transfer.result", "key2", "{}");
        when(outboxRepository.lockPendingBatch()).thenReturn(List.of(entry));
        @SuppressWarnings("unchecked")
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failed =
                new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failed);

        // Must not throw — relay continues on error
        assertThatNoException().isThrownBy(() -> relay.relay());

        assertThat(entry.getStatus()).isEqualTo("PENDING");
        verify(outboxRepository, never()).save(entry);
    }

    @Test
    void relay_multiplePendingMessages_processesAll() throws Exception {
        OutboxJpaEntity e1 = pendingEntry("transfer.result", "k1", "{}");
        OutboxJpaEntity e2 = pendingEntry("transfer.result", "k2", "{}");
        when(outboxRepository.lockPendingBatch()).thenReturn(List.of(e1, e2));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.relay();

        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), anyString());
        assertThat(e1.getStatus()).isEqualTo("SENT");
        assertThat(e2.getStatus()).isEqualTo("SENT");
    }
}
