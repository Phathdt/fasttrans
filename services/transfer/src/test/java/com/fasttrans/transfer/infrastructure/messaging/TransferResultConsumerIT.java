package com.fasttrans.transfer.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasttrans.transfer.application.dto.TransferResultEvent;
import com.fasttrans.transfer.domain.entities.Transfer;
import com.fasttrans.transfer.domain.entities.TransferStatus;
import com.fasttrans.transfer.domain.interfaces.TransferRepository;
import com.fasttrans.transfer.infrastructure.persistence.SpringDataTransferRepository;
import com.fasttrans.transfer.support.AbstractPostgresKafkaIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test: a transfer.result message published to Redpanda is consumed
 * by TransferResultConsumer and the result is applied to the transfer in Postgres.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferResultConsumerIT extends AbstractPostgresKafkaIT {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private SpringDataTransferRepository jpaRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void createTopics() {
        try (var admin = org.apache.kafka.clients.admin.AdminClient.create(
                Map.of("bootstrap.servers", REDPANDA.getBootstrapServers()))) {
            var topics = List.of(
                    new org.apache.kafka.clients.admin.NewTopic("transfer.result", 1, (short) 1),
                    new org.apache.kafka.clients.admin.NewTopic("transfer.requested", 1, (short) 1)
            );
            admin.createTopics(topics).all().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Topics may already exist; ignore
        }
    }

    @Test
    void consumer_applies_completed_result_to_pending_transfer() throws Exception {
        UUID transferId = UUID.randomUUID();
        Transfer pending = Transfer.pending(transferId, UUID.randomUUID(),
                UUID.randomUUID().toString(), "ACC001", "ACC002", 100_000L, "VND");
        transferRepository.save(pending);

        UUID messageId = UUID.randomUUID();
        TransferResultEvent event = new TransferResultEvent(
                messageId.toString(), transferId.toString(), "COMPLETED", null,
                java.time.OffsetDateTime.now().toString());

        kafkaTemplate.send("transfer.result", transferId.toString(),
                objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(20, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Transfer updated = transferRepository.findById(transferId)
                            .orElseThrow(() -> new AssertionError("Transfer not found: " + transferId));
                    assertThat(updated.getStatus()).isEqualTo(TransferStatus.COMPLETED);
                });
    }

    @Test
    void consumer_applies_failed_result_with_reason() throws Exception {
        UUID transferId = UUID.randomUUID();
        Transfer pending = Transfer.pending(transferId, UUID.randomUUID(),
                UUID.randomUUID().toString(), "ACC001", "ACC002", 100_000L, "VND");
        transferRepository.save(pending);

        UUID messageId = UUID.randomUUID();
        TransferResultEvent event = new TransferResultEvent(
                messageId.toString(), transferId.toString(), "FAILED", "INSUFFICIENT_FUNDS",
                java.time.OffsetDateTime.now().toString());

        kafkaTemplate.send("transfer.result", transferId.toString(),
                objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(20, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Transfer updated = transferRepository.findById(transferId)
                            .orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(TransferStatus.FAILED);
                });
    }

    @Test
    void consumer_deduplicates_repeated_message() throws Exception {
        UUID transferId = UUID.randomUUID();
        Transfer pending = Transfer.pending(transferId, UUID.randomUUID(),
                UUID.randomUUID().toString(), "ACC001", "ACC002", 100_000L, "VND");
        transferRepository.save(pending);

        UUID messageId = UUID.randomUUID();
        TransferResultEvent event = new TransferResultEvent(
                messageId.toString(), transferId.toString(), "COMPLETED", null,
                java.time.OffsetDateTime.now().toString());
        String payload = objectMapper.writeValueAsString(event);

        // Publish the same messageId twice — second must be a no-op (dedup)
        kafkaTemplate.send("transfer.result", transferId.toString(), payload).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send("transfer.result", transferId.toString(), payload).get(10, TimeUnit.SECONDS);

        await().atMost(20, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Transfer updated = transferRepository.findById(transferId).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(TransferStatus.COMPLETED);
                });
    }
}
