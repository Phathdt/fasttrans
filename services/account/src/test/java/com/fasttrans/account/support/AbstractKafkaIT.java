package com.fasttrans.account.support;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.redpanda.RedpandaContainer;

import java.util.List;
import java.util.Map;

/**
 * Singleton Redpanda container shared across all Kafka integration tests.
 * Topics are created eagerly in the static block because auto-create may be disabled.
 */
public abstract class AbstractKafkaIT extends AbstractPostgresIT {

    public static final RedpandaContainer REDPANDA =
            new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.2.7");

    static {
        REDPANDA.start();
        createTopics("transfer.requested", "transfer.result");
    }

    private static void createTopics(String... topicNames) {
        Map<String, Object> config = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers()
        );
        try (AdminClient admin = AdminClient.create(config)) {
            List<NewTopic> topics = List.of(topicNames).stream()
                    .map(name -> new NewTopic(name, 1, (short) 1))
                    .toList();
            admin.createTopics(topics).all().get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Kafka topics", e);
        }
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", REDPANDA::getBootstrapServers);
    }
}
