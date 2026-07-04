package com.fasttrans.account.application.services;

import com.fasttrans.account.domain.entities.Account;
import com.fasttrans.account.domain.interfaces.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountQueryServiceTest {

    @Mock AccountRepository accountRepository;

    AccountQueryService service;

    @BeforeEach
    void setUp() {
        service = new AccountQueryService(accountRepository);
    }

    private Account account(UUID id, UUID userId) {
        return new Account(id, "ref-" + id, userId, "owner", 500L, "VND", Instant.now());
    }

    // ── validateOwnership ─────────────────────────────────────────────────────

    @Test
    void validateOwnership_accountExistsAndBelongsToUser_returnsTrue() {
        UUID userId = UUID.randomUUID();
        Account a = account(UUID.randomUUID(), userId);
        when(accountRepository.findByAccountRef("ref")).thenReturn(Optional.of(a));

        assertThat(service.validateOwnership("ref", userId)).isTrue();
    }

    @Test
    void validateOwnership_accountExistsButDifferentOwner_returnsFalse() {
        UUID userId = UUID.randomUUID();
        Account a = account(UUID.randomUUID(), UUID.randomUUID()); // different owner
        when(accountRepository.findByAccountRef("ref")).thenReturn(Optional.of(a));

        assertThat(service.validateOwnership("ref", userId)).isFalse();
    }

    @Test
    void validateOwnership_accountNotFound_returnsFalse() {
        when(accountRepository.findByAccountRef("nonexistent")).thenReturn(Optional.empty());

        assertThat(service.validateOwnership("nonexistent", UUID.randomUUID())).isFalse();
    }

    // ── listAccounts ──────────────────────────────────────────────────────────

    @Test
    void listAccounts_returnsAllAccountsForUser() {
        UUID userId = UUID.randomUUID();
        List<Account> accounts = List.of(
                account(UUID.randomUUID(), userId),
                account(UUID.randomUUID(), userId)
        );
        when(accountRepository.findByUserId(userId)).thenReturn(accounts);

        assertThat(service.listAccounts(userId)).hasSize(2);
    }

    @Test
    void listAccounts_noAccounts_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(accountRepository.findByUserId(userId)).thenReturn(List.of());

        assertThat(service.listAccounts(userId)).isEmpty();
    }

    // ── lookup ────────────────────────────────────────────────────────────────

    @Test
    void lookup_foundAccount_returnsNonEmpty() {
        Account a = account(UUID.randomUUID(), UUID.randomUUID());
        when(accountRepository.findByAccountRef("ref")).thenReturn(Optional.of(a));

        assertThat(service.lookup("ref")).contains(a);
    }

    @Test
    void lookup_notFound_returnsEmpty() {
        when(accountRepository.findByAccountRef("missing")).thenReturn(Optional.empty());

        assertThat(service.lookup("missing")).isEmpty();
    }
}
