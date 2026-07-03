package com.fasttrans.account.kafka;

import com.fasttrans.account.entity.OutboxEntity;
import com.fasttrans.account.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox relay — polls the outbox table every 1s, publishes to Kafka, marks SENT.
 * FOR UPDATE SKIP LOCKED: safe when running multiple instances (demo: 1 instance).
 * kafkaTemplate.send().get() — blocks waiting for broker ack before committing SENT.
 */
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

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<OutboxEntity> pending = outboxRepository.lockPendingBatch();
        if (pending.isEmpty()) return;

        log.debug("OutboxRelay: {} pending messages", pending.size());

        for (OutboxEntity entry : pending) {
            try {
                // Block waiting for broker ack — ensures at-least-once before marking SENT
                kafkaTemplate.send(entry.getTopic(), entry.getMsgKey(), entry.getPayload()).get();
                entry.markSent();
                outboxRepository.save(entry);
                log.debug("Relayed outbox id={} topic={} key={}", entry.getId(), entry.getTopic(), entry.getMsgKey());
            } catch (Exception e) {
                // Do not mark SENT — will retry on the next poll (at-least-once)
                log.error("Failed to relay outbox id={} topic={}: {}", entry.getId(), entry.getTopic(), e.getMessage());
            }
        }
    }
}
