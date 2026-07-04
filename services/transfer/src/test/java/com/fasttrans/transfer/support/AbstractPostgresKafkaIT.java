package com.fasttrans.transfer.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.redpanda.RedpandaContainer;

/**
 * Base class for integration tests that need BOTH Postgres AND a Kafka-compatible broker.
 * Extends AbstractPostgresIT (inherits its @DynamicPropertySource for datasource properties)
 * and adds a second @DynamicPropertySource for Kafka bootstrap servers.
 *
 * Containers are singletons started once per JVM and reused across all subclass tests.
 */
public abstract class AbstractPostgresKafkaIT extends AbstractPostgresIT {

    public static final RedpandaContainer REDPANDA =
            new RedpandaContainer("redpandadata/redpanda:v24.2.7");

    static {
        REDPANDA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", REDPANDA::getBootstrapServers);
    }
}
