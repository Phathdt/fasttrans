package com.fasttrans.transfer.dto;

// Event published to the transfer.requested topic (key = fromAccountRef). Matches docs/events/transfer-events.md.
public record TransferRequestedEvent(
        String messageId,
        String transferId,
        String fromAccountRef,
        String toAccountRef,
        long amount,
        String currency,
        String requestedAt
) {
}
