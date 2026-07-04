package com.fasttrans.account.infrastructure.persistence;

import com.fasttrans.account.domain.entities.LedgerEntry;
import com.fasttrans.account.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class LedgerRepositoryIT extends AbstractPostgresIT {

    @Autowired SpringDataLedgerRepository jpaRepo;

    // Seeded account id from migration
    private static final UUID ALICE_ACCOUNT_ID =
            UUID.fromString("aaaaaaa1-0000-0000-0000-000000000001");

    @Test
    void save_debitEntry_persistsCorrectly() {
        UUID transferId = UUID.randomUUID();
        LedgerEntry entry = LedgerEntry.debit(ALICE_ACCOUNT_ID, transferId, 100_000L, 900_000L);

        LedgerRepositoryImpl repo = new LedgerRepositoryImpl(jpaRepo);
        repo.save(entry);

        LedgerEntryJpaEntity found = jpaRepo.findById(entry.getId()).orElseThrow();
        assertThat(found.getDirection()).isEqualTo("DEBIT");
        assertThat(found.getAmount()).isEqualTo(100_000L);
        assertThat(found.getBalanceAfter()).isEqualTo(900_000L);
        assertThat(found.getAccountId()).isEqualTo(ALICE_ACCOUNT_ID);
        assertThat(found.getTransferId()).isEqualTo(transferId);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void save_creditEntry_persistsCorrectly() {
        UUID transferId = UUID.randomUUID();
        UUID bobAccountId = UUID.fromString("bbbbbbb1-0000-0000-0000-000000000001");
        LedgerEntry entry = LedgerEntry.credit(bobAccountId, transferId, 100_000L, 100_000L);

        LedgerRepositoryImpl repo = new LedgerRepositoryImpl(jpaRepo);
        repo.save(entry);

        LedgerEntryJpaEntity found = jpaRepo.findById(entry.getId()).orElseThrow();
        assertThat(found.getDirection()).isEqualTo("CREDIT");
        assertThat(found.getBalanceAfter()).isEqualTo(100_000L);
    }
}
