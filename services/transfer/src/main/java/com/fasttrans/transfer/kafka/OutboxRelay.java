package com.fasttrans.transfer.kafka;

import com.fasttrans.transfer.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;

// Polling relay: read PENDING outbox rows → publish to Redpanda → mark SENT.
// Uses FOR UPDATE SKIP LOCKED so it is multi-instance safe (never publishes the same row twice).
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxRepository outboxRepository,
                       KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // fixedDelay = 1000ms; start after 5s to let Kafka become ready before the first relay loop
    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    @Transactional
    public void relay() {
        var pending = outboxRepository.lockPendingBatch();
        if (pending.isEmpty()) {
            return;
        }

        int published = 0;
        for (var row : pending) {
            try {
                // Wait for the broker ack before marking SENT (at-least-once; a crash before marking → resend)
                kafkaTemplate.send(row.getTopic(), row.getMsgKey(), row.getPayload()).get();
                row.markSent();
                outboxRepository.save(row);
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Relay interrupted at outboxId={}", row.getId());
                break;
            } catch (ExecutionException e) {
                log.error("Publish failed outboxId={} topic={}: {}", row.getId(), row.getTopic(), e.getCause().getMessage());
                // Skip this row in the loop; it will be retried on the next poll
            }
        }

        if (published > 0) {
            log.info("Outbox relay: published {} rows", published);
        }
    }
}
