package com.fasttrans.account.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Event received from the transfer.requested topic — contract matches transfer-events.md.
 * Uses a record for immutability; ObjectMapper deserializes it in the messaging layer.
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
