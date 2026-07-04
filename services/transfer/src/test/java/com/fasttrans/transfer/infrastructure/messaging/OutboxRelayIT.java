package com.fasttrans.transfer.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasttrans.transfer.application.dto.TransferRequestedEvent;
import com.fasttrans.transfer.infrastructure.persistence.OutboxJpaEntity;
import com.fasttrans.transfer.infrastructure.persistence.SpringDataOutboxRepository;
import com.fasttrans.transfer.support.AbstractPostgresKafkaIT;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test: OutboxRelay reads PENDING outbox rows from Postgres and publishes to Redpanda.
 * Verifies that after relay() the row is marked SENT and a message arrives on the broker.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxRelayIT extends AbstractPostgresKafkaIT {

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private SpringDataOutboxRepository outboxRepository;

    @BeforeAll
    static void createTopics() {
        try (var admin = org.apache.kafka.clients.admin.AdminClient.create(
                Map.of("bootstrap.servers", REDPANDA.getBootstrapServers()))) {
            var topics = List.of(
                    new org.apache.kafka.clients.admin.NewTopic("transfer.requested", 1, (short) 1));
            admin.createTopics(topics).all().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Topic may already exist; ignore
        }
    }

    @Test
    void relay_publishes_pending_outbox_row_and_marks_it_sent() throws Exception {
        UUID transferId = UUID.randomUUID();
        String payload = new ObjectMapper().writeValueAsString(new TransferRequestedEvent(
                UUID.randomUUID().toString(), transferId.toString(),
                "ACC001", "ACC002", 50_000L, "VND", OffsetDateTime.now().toString()));

        OutboxJpaEntity row = OutboxJpaEntity.pending(
                UUID.randomUUID(), transferId, "transfer.requested", "ACC001", payload);
        outboxRepository.save(row);

        // Invoke relay directly (bypasses @Scheduled initial delay)
        outboxRelay.relay();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            OutboxJpaEntity updated = outboxRepository.findById(row.getId()).orElseThrow();
            // status field is private; verify via lockPendingBatch — sent rows must not appear
            List<OutboxJpaEntity> pending = outboxRepository.lockPendingBatch();
            assertThat(pending).noneMatch(e -> e.getId().equals(row.getId()));
        });

        // Verify a message arrived on the broker with the correct transferId in the payload
        List<String> received = consumeFromTopic("transfer.requested", 1);
        assertThat(received).isNotEmpty();
        assertThat(received.get(0)).contains(transferId.toString());
    }

    private List<String> consumeFromTopic(String topic, int expectedCount) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "relay-it-verifier-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
        List<String> results = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 10_000;
            while (results.size() < expectedCount && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> r : records) {
                    results.add(r.value());
                }
            }
        }
        return results;
    }
}
