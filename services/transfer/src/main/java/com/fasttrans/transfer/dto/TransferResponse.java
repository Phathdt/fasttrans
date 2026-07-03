package com.fasttrans.transfer.dto;

import com.fasttrans.transfer.entity.TransferEntity;

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
    public static TransferResponse from(TransferEntity t) {
        return new TransferResponse(
                t.getId().toString(),
                t.getFromAccountRef(),
                t.getToAccountRef(),
                t.getAmount(),
                t.getCurrency(),
                t.getStatus(),
                t.getReason(),
                t.getCreatedAt()
        );
    }
}
