package com.fasttrans.account.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Event written to the outbox then published to the transfer.result topic — contract matches transfer-events.md.
 * status: COMPLETED | FAILED.
 * reason: null when COMPLETED; INSUFFICIENT_FUNDS | ACCOUNT_NOT_FOUND when FAILED.
 */
public record TransferResultEvent(
        UUID messageId,
        UUID transferId,
        String status,
        String reason,
        Instant processedAt
) {}
