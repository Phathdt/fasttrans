package com.fasttrans.account.infrastructure.messaging;

import com.fasttrans.account.domain.entities.TransferResult;
import com.fasttrans.account.infrastructure.persistence.OutboxJpaEntity;
import com.fasttrans.account.infrastructure.persistence.SpringDataOutboxRepository;
import com.fasttrans.account.support.AbstractKafkaIT;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

/**
 * Integration test for OutboxRelay: verifies that PENDING outbox entries are
 * picked up by the relay scheduler and actually delivered to Kafka.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxRelayIT extends AbstractKafkaIT {

    @Autowired SpringDataOutboxRepository outboxRepository;
    @Autowired OutboxRelay outboxRelay;

    @BeforeEach
    void cleanOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    void relay_pendingEntry_isPublishedToKafkaAndMarkedSent() {
        // Insert a PENDING entry directly (bypassing the domain layer)
        UUID transferId = UUID.randomUUID();
        OutboxJpaEntity entry = OutboxJpaEntity.pending(
                transferId, "transfer.result", transferId.toString(),
                "{\"status\":\"COMPLETED\",\"transferId\":\"" + transferId + "\"}");
        outboxRepository.save(entry);

        // Use a real Kafka consumer to verify the message arrives on the topic
        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-relay-verify-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("transfer.result"));

            // Trigger relay manually (scheduled runs every 1s, but we call directly for determinism)
            outboxRelay.relay();

            // Wait for message to appear on topic
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                assertThat(records.count()).isGreaterThan(0);
            });
        }

        // Entry should be marked SENT
        OutboxJpaEntity updated = outboxRepository.findById(entry.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("SENT");
        assertThat(updated.getSentAt()).isNotNull();
    }

    @Test
    void relay_noEntries_doesNothing() {
        // Should not throw; nothing to relay
        assertThatNoException().isThrownBy(() -> outboxRelay.relay());
    }
}
