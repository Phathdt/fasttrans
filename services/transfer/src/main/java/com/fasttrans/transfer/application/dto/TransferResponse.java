package com.fasttrans.transfer.application.dto;

import com.fasttrans.transfer.domain.entities.Transfer;

import java.time.OffsetDateTime;

public record TransferResponse(
        String id,
        String fromAccountRef,
        String toAccountRef,
        long amount,
        String currency,
        String status,
        String reason,
        OffsetDateTime createdAt
) {
    public static TransferResponse from(Transfer t) {
        return new TransferResponse(
                t.getId().toString(),
                t.getFromAccountRef(),
                t.getToAccountRef(),
                t.getAmount(),
                t.getCurrency(),
                t.getStatus().name(),
                t.getReason(),
                t.getCreatedAt()
        );
    }
}
