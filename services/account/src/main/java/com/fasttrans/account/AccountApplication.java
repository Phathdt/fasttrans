package com.fasttrans.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point — Account Service.
 * Exposes REST (accounts + lookup) on port 8080, gRPC server on port 9090, and Kafka consumer/producer.
 */
@SpringBootApplication
@EnableScheduling
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
