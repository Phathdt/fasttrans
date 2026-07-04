package com.fasttrans.account.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasttrans.account.application.dto.TransferRequestedEvent;
import com.fasttrans.account.application.services.ProcessTransferService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferRequestedConsumerTest {

    @Mock ProcessTransferService processTransferService;

    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    TransferRequestedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TransferRequestedConsumer(processTransferService, objectMapper);
    }

    private String validPayload(UUID messageId, UUID transferId) throws Exception {
        TransferRequestedEvent ev = new TransferRequestedEvent(
                messageId, transferId, "100000000001", "200000000001", 500L, "VND", Instant.now());
        return objectMapper.writeValueAsString(ev);
    }

    @Test
    void onMessage_validPayload_delegatesToService() throws Exception {
        UUID messageId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        String payload = validPayload(messageId, transferId);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("transfer.requested", 0, 0L, "key", payload);

        consumer.onMessage(record);

        ArgumentCaptor<TransferRequestedEvent> cap = ArgumentCaptor.forClass(TransferRequestedEvent.class);
        verify(processTransferService).process(cap.capture());
        assertThat(cap.getValue().messageId()).isEqualTo(messageId);
        assertThat(cap.getValue().transferId()).isEqualTo(transferId);
    }

    @Test
    void onMessage_invalidJson_logsErrorAndDoesNotThrow() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("transfer.requested", 0, 0L, "key", "not-valid-json{{{");

        // Must not throw — Spring Kafka will ack and move on
        assertThatNoException().isThrownBy(() -> consumer.onMessage(record));
        verifyNoInteractions(processTransferService);
    }

    @Test
    void onMessage_serviceThrows_logsErrorAndDoesNotPropagate() throws Exception {
        String payload = validPayload(UUID.randomUUID(), UUID.randomUUID());
        ConsumerRecord<String, String> record = new ConsumerRecord<>("transfer.requested", 0, 0L, "key", payload);
        doThrow(new RuntimeException("service failure")).when(processTransferService).process(any());

        assertThatNoException().isThrownBy(() -> consumer.onMessage(record));
    }
}
