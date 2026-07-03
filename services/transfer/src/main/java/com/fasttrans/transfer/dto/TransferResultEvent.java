package com.fasttrans.transfer.dto;

// Event consumed from the transfer.result topic (key = transferId). Matches docs/events/transfer-events.md.
public record TransferResultEvent(
        String messageId,
        String transferId,
        String status,   // COMPLETED|FAILED
        String reason,   // null when COMPLETED; INSUFFICIENT_FUNDS|ACCOUNT_NOT_FOUND when FAILED
        String processedAt
) {
}
