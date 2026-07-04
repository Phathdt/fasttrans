package com.fasttrans.account.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasttrans.account.application.dto.TransferRequestedEvent;
import com.fasttrans.account.infrastructure.persistence.OutboxJpaEntity;
import com.fasttrans.account.infrastructure.persistence.SpringDataOutboxRepository;
import com.fasttrans.account.infrastructure.persistence.SpringDataProcessedMessageRepository;
import com.fasttrans.account.support.AbstractKafkaIT;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

/**
 * Integration test for TransferRequestedConsumer using a real Redpanda container.
 * Publishes to transfer.requested and asserts the outbox contains the result.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferRequestedConsumerIT extends AbstractKafkaIT {

    @Autowired SpringDataOutboxRepository outboxRepository;
    @Autowired SpringDataProcessedMessageRepository inboxRepository;

    private KafkaTemplate<String, String> producer;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        inboxRepository.deleteAll();

        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "1"
        );
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Test
    void consumer_validTransfer_completedResultAppearsInOutbox() throws Exception {
        TransferRequestedEvent ev = new TransferRequestedEvent(
                UUID.randomUUID(), UUID.randomUUID(),
                "100000000001", "200000000001",
                10_000L, "VND", Instant.now());
        String payload = mapper.writeValueAsString(ev);

        producer.send("transfer.requested", ev.fromAccountRef(), payload).get(5, TimeUnit.SECONDS);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OutboxJpaEntity> outbox = outboxRepository.findAll();
            assertThat(outbox).isNotEmpty();
            assertThat(outbox.get(0).getPayload()).contains("COMPLETED");
        });
    }

    @Test
    void consumer_insufficientFunds_failedResultAppearsInOutbox() throws Exception {
        // Use Long.MAX_VALUE to guarantee insufficient funds regardless of current balance
        TransferRequestedEvent ev = new TransferRequestedEvent(
                UUID.randomUUID(), UUID.randomUUID(),
                "200000000001", "100000000001",
                Long.MAX_VALUE, "VND", Instant.now());
        String payload = mapper.writeValueAsString(ev);

        producer.send("transfer.requested", ev.fromAccountRef(), payload).get(5, TimeUnit.SECONDS);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OutboxJpaEntity> outbox = outboxRepository.findAll();
            assertThat(outbox).isNotEmpty();
            assertThat(outbox.get(0).getPayload()).contains("INSUFFICIENT_FUNDS");
        });
    }
}
