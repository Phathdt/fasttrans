package com.fasttrans.transfer.domain.entities;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferTest {

    private static Transfer makeTransfer(TransferStatus status) {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        return new Transfer(id, userId, "idem-1", "ACC001", "ACC002",
                100_000L, "VND", status, null, now, now);
    }

    @Test
    void pending_factory_sets_status_and_fields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Transfer t = Transfer.pending(id, userId, "key-1", "ACC001", "ACC002", 50_000L, "VND");

        assertThat(t.getId()).isEqualTo(id);
        assertThat(t.getUserId()).isEqualTo(userId);
        assertThat(t.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(t.getFromAccountRef()).isEqualTo("ACC001");
        assertThat(t.getToAccountRef()).isEqualTo("ACC002");
        assertThat(t.getAmount()).isEqualTo(50_000L);
        assertThat(t.getCurrency()).isEqualTo("VND");
        assertThat(t.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(t.getReason()).isNull();
        assertThat(t.getCreatedAt()).isNotNull();
        assertThat(t.getUpdatedAt()).isNotNull();
    }

    @Test
    void isPending_returns_true_for_pending_transfer() {
        Transfer t = makeTransfer(TransferStatus.PENDING);
        assertThat(t.isPending()).isTrue();
    }

    @Test
    void isPending_returns_false_for_completed_transfer() {
        Transfer t = makeTransfer(TransferStatus.COMPLETED);
        assertThat(t.isPending()).isFalse();
    }

    @Test
    void isPending_returns_false_for_failed_transfer() {
        Transfer t = makeTransfer(TransferStatus.FAILED);
        assertThat(t.isPending()).isFalse();
    }

    @Test
    void applyResult_sets_completed_status() {
        Transfer t = makeTransfer(TransferStatus.PENDING);
        t.applyResult("COMPLETED", null);

        assertThat(t.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(t.getReason()).isNull();
        assertThat(t.getUpdatedAt()).isNotNull();
    }

    @Test
    void applyResult_sets_failed_status_with_reason() {
        Transfer t = makeTransfer(TransferStatus.PENDING);
        t.applyResult("FAILED", "INSUFFICIENT_FUNDS");

        assertThat(t.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(t.getReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void applyResult_throws_for_invalid_status() {
        Transfer t = makeTransfer(TransferStatus.PENDING);
        assertThatThrownBy(() -> t.applyResult("INVALID", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transferStatus_enum_has_three_values() {
        assertThat(TransferStatus.values()).containsExactlyInAnyOrder(
                TransferStatus.PENDING, TransferStatus.COMPLETED, TransferStatus.FAILED);
    }

    @Test
    void default_constructor_and_setters_work() {
        Transfer t = new Transfer();
        UUID id = UUID.randomUUID();
        t.setId(id);
        t.setStatus(TransferStatus.PENDING);
        assertThat(t.getId()).isEqualTo(id);
        assertThat(t.getStatus()).isEqualTo(TransferStatus.PENDING);
    }
}
