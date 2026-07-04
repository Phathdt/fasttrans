package com.fasttrans.account.infrastructure.persistence;

import com.fasttrans.account.domain.entities.Account;
import com.fasttrans.account.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AccountRepositoryImpl.class, AccountMapperImpl.class})
class AccountRepositoryImplIT extends AbstractPostgresIT {

    @Autowired AccountRepositoryImpl repo;

    // Seed data from V20260703090000 migration
    private static final UUID ALICE_ACCOUNT_ID =
            UUID.fromString("aaaaaaa1-0000-0000-0000-000000000001");
    private static final String ALICE_REF = "100000000001";
    private static final UUID ALICE_USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void findByAccountRef_existingRef_returnsDomainAccount() {
        Optional<Account> result = repo.findByAccountRef(ALICE_REF);

        assertThat(result).isPresent();
        assertThat(result.get().getAccountRef()).isEqualTo(ALICE_REF);
        assertThat(result.get().getUserId()).isEqualTo(ALICE_USER_ID);
        // Balance is mutable across tests — only verify it is non-negative
        assertThat(result.get().getBalance()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void findByAccountRef_unknownRef_returnsEmpty() {
        assertThat(repo.findByAccountRef("000000000000")).isEmpty();
    }

    @Test
    void findByUserId_returnsAllAccountsForUser() {
        var accounts = repo.findByUserId(ALICE_USER_ID);
        // Alice has 2 seeded accounts
        assertThat(accounts).hasSize(2);
        assertThat(accounts).allMatch(a -> a.getUserId().equals(ALICE_USER_ID));
    }

    @Test
    void lockById_existingAccount_returnsDomainAccountWithPessimisticLock() {
        // lockById runs SELECT FOR UPDATE — must succeed inside a transaction (DataJpaTest wraps each test)
        Optional<Account> result = repo.lockById(ALICE_ACCOUNT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(ALICE_ACCOUNT_ID);
    }

    @Test
    void lockById_unknownId_returnsEmpty() {
        assertThat(repo.lockById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void save_updatesBalance() {
        Account account = repo.findByAccountRef(ALICE_REF).orElseThrow();
        long before = account.getBalance();
        account.credit(50_000L);
        repo.save(account);

        Account reloaded = repo.findByAccountRef(ALICE_REF).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualTo(before + 50_000L);
    }
}
