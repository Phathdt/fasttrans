package com.fasttrans.account.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Event received from the transfer.requested topic — contract matches transfer-events.md.
 * Uses a record for immutability; ObjectMapper deserializes manually in the consumer.
 */
public record TransferRequestedEvent(
        UUID messageId,
        UUID transferId,
        String fromAccountRef,
        String toAccountRef,
        long amount,
        String currency,
        Instant requestedAt
) {}
