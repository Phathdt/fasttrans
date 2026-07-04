package com.fasttrans.transfer.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasttrans.transfer.application.dto.TransferResultEvent;
import com.fasttrans.transfer.application.services.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferResultConsumerTest {

    @Mock
    private TransferService transferService;

    private TransferResultConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new TransferResultConsumer(transferService, objectMapper);
    }

    @Test
    void consume_valid_json_calls_applyResult() throws Exception {
        TransferResultEvent event = new TransferResultEvent(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "COMPLETED",
                null,
                "2026-07-04T08:00:00Z"
        );
        String payload = objectMapper.writeValueAsString(event);

        consumer.consume(payload);

        verify(transferService).applyResult(any(TransferResultEvent.class));
    }

    @Test
    void consume_valid_failed_event_calls_applyResult() throws Exception {
        TransferResultEvent event = new TransferResultEvent(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "FAILED",
                "INSUFFICIENT_FUNDS",
                "2026-07-04T08:00:00Z"
        );
        String payload = objectMapper.writeValueAsString(event);

        consumer.consume(payload);

        verify(transferService).applyResult(any(TransferResultEvent.class));
    }

    @Test
    void consume_bad_json_swallows_exception_and_does_not_call_service() {
        // Should not throw; bad messages are logged and dropped
        consumer.consume("not-valid-json{{");

        verifyNoInteractions(transferService);
    }

    @Test
    void consume_empty_payload_swallows_exception() {
        consumer.consume("");

        verifyNoInteractions(transferService);
    }
}
