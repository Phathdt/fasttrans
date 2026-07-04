package com.fasttrans.transfer.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.redpanda.RedpandaContainer;

/**
 * Base class for integration tests requiring a real Kafka-compatible broker.
 * Uses RedpandaContainer (image: redpandadata/redpanda:v24.2.7) as a singleton.
 * Docker API version override is handled via -Dapi.version=1.44 in the failsafe argLine.
 */
public abstract class AbstractKafkaIT {

    public static final RedpandaContainer REDPANDA;

    static {
        REDPANDA = new RedpandaContainer("redpandadata/redpanda:v24.2.7");
        REDPANDA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", REDPANDA::getBootstrapServers);
    }
}
