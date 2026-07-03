package com.fasttrans.transfer.kafka;

import com.fasttrans.transfer.dto.TransferResultEvent;
import com.fasttrans.transfer.service.TransferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Consumer group "transfer-service" listens to transfer.result.
// Parse JSON manually (String payload) to avoid complex trusted-packages config.
@Component
public class TransferResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferResultConsumer.class);

    private final TransferService transferService;
    private final ObjectMapper objectMapper;

    public TransferResultConsumer(TransferService transferService, ObjectMapper objectMapper) {
        this.transferService = transferService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "transfer.result", groupId = "transfer-service")
    public void consume(String payload) {
        try {
            TransferResultEvent event = objectMapper.readValue(payload, TransferResultEvent.class);
            log.debug("Received transfer.result messageId={} transferId={} status={}",
                    event.messageId(), event.transferId(), event.status());
            transferService.applyResult(event);
        } catch (Exception e) {
            log.error("Failed to process transfer.result payload={}: {}", payload, e.getMessage(), e);
            // Do not rethrow — avoid infinite retries for a corrupt message; log it for alerting.
        }
    }
}
