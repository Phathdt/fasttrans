package com.fasttrans.account.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasttrans.account.application.dto.TransferRequestedEvent;
import com.fasttrans.account.application.services.ProcessTransferService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Receives transfer.requested → parses JSON manually → calls ProcessTransferService.process().
 * Concurrency 1 — process sequentially per partition (partition key = fromAccountRef).
 */
@Component
public class TransferRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferRequestedConsumer.class);

    private final ProcessTransferService processTransferService;
    private final ObjectMapper objectMapper;

    public TransferRequestedConsumer(ProcessTransferService processTransferService, ObjectMapper objectMapper) {
        this.processTransferService = processTransferService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "transfer.requested", groupId = "account-service", concurrency = "1")
    public void onMessage(ConsumerRecord<String, String> record) {
        String payload = record.value();
        log.debug("Received transfer.requested key={} offset={}", record.key(), record.offset());
        try {
            TransferRequestedEvent event = objectMapper.readValue(payload, TransferRequestedEvent.class);
            processTransferService.process(event);
        } catch (Exception e) {
            // Log and let Spring Kafka ack — no infinite retry; bad data will never succeed.
            // Dead-letter queue is out of scope for the demo.
            log.error("Failed to process transfer.requested payload={} error={}", payload, e.getMessage(), e);
        }
    }
}
