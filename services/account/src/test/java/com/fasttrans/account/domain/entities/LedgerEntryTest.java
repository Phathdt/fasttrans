package com.fasttrans.account.domain.entities;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class LedgerEntryTest {

    @Test
    void debit_factory_setsFieldsCorrectly() {
        UUID accountId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        LedgerEntry entry = LedgerEntry.debit(accountId, transferId, 500L, 1500L);

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getAccountId()).isEqualTo(accountId);
        assertThat(entry.getTransferId()).isEqualTo(transferId);
        assertThat(entry.getDirection()).isEqualTo(LedgerEntry.DIR_DEBIT);
        assertThat(entry.getAmount()).isEqualTo(500L);
        assertThat(entry.getBalanceAfter()).isEqualTo(1500L);
        assertThat(entry.getCreatedAt()).isNull(); // set by @PrePersist
    }

    @Test
    void credit_factory_setsFieldsCorrectly() {
        UUID accountId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        LedgerEntry entry = LedgerEntry.credit(accountId, transferId, 300L, 800L);

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getAccountId()).isEqualTo(accountId);
        assertThat(entry.getTransferId()).isEqualTo(transferId);
        assertThat(entry.getDirection()).isEqualTo(LedgerEntry.DIR_CREDIT);
        assertThat(entry.getAmount()).isEqualTo(300L);
        assertThat(entry.getBalanceAfter()).isEqualTo(800L);
    }

    @Test
    void debitAndCredit_produceUniqueIds() {
        UUID accountId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        LedgerEntry d = LedgerEntry.debit(accountId, transferId, 100L, 900L);
        LedgerEntry c = LedgerEntry.credit(accountId, transferId, 100L, 1100L);

        assertThat(d.getId()).isNotEqualTo(c.getId());
    }
}
