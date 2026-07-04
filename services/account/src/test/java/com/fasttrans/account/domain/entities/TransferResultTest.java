package com.fasttrans.account.domain.entities;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class TransferResultTest {

    @Test
    void completed_setsStatusAndNullReason() {
        UUID transferId = UUID.randomUUID();
        TransferResult r = TransferResult.completed(transferId);
        assertThat(r.transferId()).isEqualTo(transferId);
        assertThat(r.status()).isEqualTo(TransferResult.STATUS_COMPLETED);
        assertThat(r.reason()).isNull();
    }

    @Test
    void accountNotFound_setsFailedStatusAndReason() {
        UUID transferId = UUID.randomUUID();
        TransferResult r = TransferResult.accountNotFound(transferId);
        assertThat(r.transferId()).isEqualTo(transferId);
        assertThat(r.status()).isEqualTo(TransferResult.STATUS_FAILED);
        assertThat(r.reason()).isEqualTo(TransferResult.REASON_NOT_FOUND);
    }

    @Test
    void insufficientFunds_setsFailedStatusAndReason() {
        UUID transferId = UUID.randomUUID();
        TransferResult r = TransferResult.insufficientFunds(transferId);
        assertThat(r.transferId()).isEqualTo(transferId);
        assertThat(r.status()).isEqualTo(TransferResult.STATUS_FAILED);
        assertThat(r.reason()).isEqualTo(TransferResult.REASON_INSUFFICIENT);
    }
}
