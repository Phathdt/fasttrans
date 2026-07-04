package com.fasttrans.account.domain.entities;

import com.fasttrans.account.domain.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AccountTest {

    private Account account(long balance) {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        return new Account(id, "100000000001", userId, "alice", balance, "VND", Instant.now());
    }

    @Test
    void debit_sufficientFunds_reducesBalance() {
        Account a = account(1000L);
        a.debit(400L);
        assertThat(a.getBalance()).isEqualTo(600L);
    }

    @Test
    void debit_exactBalance_reducesToZero() {
        Account a = account(500L);
        a.debit(500L);
        assertThat(a.getBalance()).isZero();
    }

    @Test
    void debit_insufficientFunds_throwsInsufficientFundsException() {
        Account a = account(100L);
        assertThatThrownBy(() -> a.debit(200L))
                .isInstanceOf(InsufficientFundsException.class)
                .satisfies(ex -> {
                    InsufficientFundsException ife = (InsufficientFundsException) ex;
                    assertThat(ife.getBalance()).isEqualTo(100L);
                    assertThat(ife.getAmount()).isEqualTo(200L);
                    assertThat(ife.getAccountId()).isEqualTo(a.getId());
                });
    }

    @Test
    void credit_increasesBalance() {
        Account a = account(300L);
        a.credit(200L);
        assertThat(a.getBalance()).isEqualTo(500L);
    }

    @Test
    void isOwnedBy_matchingUserId_returnsTrue() {
        UUID userId = UUID.randomUUID();
        Account a = new Account(UUID.randomUUID(), "ref", userId, "bob", 0L, "VND", Instant.now());
        assertThat(a.isOwnedBy(userId)).isTrue();
    }

    @Test
    void isOwnedBy_differentUserId_returnsFalse() {
        Account a = account(0L);
        assertThat(a.isOwnedBy(UUID.randomUUID())).isFalse();
    }
}
