package com.fasttrans.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateTransferRequest(
        @NotBlank String fromAccountRef,
        @NotBlank String toAccountRef,
        @Positive long amount,
        @NotBlank String currency
) {
}
