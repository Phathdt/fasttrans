package com.fasttrans.account.application.services;

import com.fasttrans.account.application.dto.TransferRequestedEvent;
import com.fasttrans.account.domain.entities.Account;
import com.fasttrans.account.domain.entities.LedgerEntry;
import com.fasttrans.account.domain.entities.TransferResult;
import com.fasttrans.account.domain.exception.InsufficientFundsException;
import com.fasttrans.account.domain.interfaces.AccountRepository;
import com.fasttrans.account.domain.interfaces.InboxRepository;
import com.fasttrans.account.domain.interfaces.LedgerRepository;
import com.fasttrans.account.domain.interfaces.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessTransferServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock LedgerRepository ledgerRepository;
    @Mock InboxRepository inboxRepository;
    @Mock OutboxRepository outboxRepository;

    ProcessTransferService service;

    @BeforeEach
    void setUp() {
        service = new ProcessTransferService(accountRepository, ledgerRepository, inboxRepository, outboxRepository);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TransferRequestedEvent event(String fromRef, String toRef, long amount) {
        return new TransferRequestedEvent(
                UUID.randomUUID(), UUID.randomUUID(), fromRef, toRef, amount, "VND", Instant.now());
    }

    private Account account(UUID id, long balance) {
        return new Account(id, "ref-" + id, UUID.randomUUID(), "owner", balance, "VND", Instant.now());
    }

    // ── inbox dedup ───────────────────────────────────────────────────────────

    @Test
    void process_duplicateMessage_skipsProcessing() {
        TransferRequestedEvent ev = event("from", "to", 100L);
        when(inboxRepository.isProcessed(ev.messageId())).thenReturn(true);

        service.process(ev);

        verifyNoInteractions(accountRepository, ledgerRepository, outboxRepository);
    }

    // ── account not found ─────────────────────────────────────────────────────

    @Test
    void process_fromAccountMissing_enqueuessAccountNotFound() {
        TransferRequestedEvent ev = event("missing-from", "existing-to", 100L);
        when(inboxRepository.isProcessed(any())).thenReturn(false);
        when(accountRepository.findByAccountRef("missing-from")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountRef("existing-to"))
                .thenReturn(Optional.of(account(UUID.randomUUID(), 500L)));

        service.process(ev);

        ArgumentCaptor<TransferResult> cap = ArgumentCaptor.forClass(TransferResult.class);
        verify(outboxRepository).enqueue(cap.capture());
        assertThat(cap.getValue().status()).isEqualTo(TransferResult.STATUS_FAILED);
        assertThat(cap.getValue().reason()).isEqualTo(TransferResult.REASON_NOT_FOUND);
        verify(inboxRepository).markProcessed(ev.messageId(), ev.transferId());
        verifyNoInteractions(ledgerRepository);
    }

    @Test
    void process_toAccountMissing_enqueuesAccountNotFound() {
        TransferRequestedEvent ev = event("existing-from", "missing-to", 100L);
        when(inboxRepository.isProcessed(any())).thenReturn(false);
        when(accountRepository.findByAccountRef("existing-from"))
                .thenReturn(Optional.of(account(UUID.randomUUID(), 500L)));
        when(accountRepository.findByAccountRef("missing-to")).thenReturn(Optional.empty());

        service.process(ev);

        ArgumentCaptor<TransferResult> cap = ArgumentCaptor.forClass(TransferResult.class);
        verify(outboxRepository).enqueue(cap.capture());
        assertThat(cap.getValue().reason()).isEqualTo(TransferResult.REASON_NOT_FOUND);
        verify(inboxRepository).markProcessed(ev.messageId(), ev.transferId());
    }

    // ── lock ordering ─────────────────────────────────────────────────────────

    @Test
    void process_locksAccountsInSortedUUIDOrder() {
        UUID id1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("ffffffff-0000-0000-0000-000000000002");

        // Ensure id1 < id2 lexicographically so expected lock order is id1 then id2
        List<UUID> expectedOrder = List.of(id1, id2).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        Account from = account(id1, 1000L);
        Account to   = account(id2, 0L);

        TransferRequestedEvent ev = event(from.getAccountRef(), to.getAccountRef(), 200L);
        when(inboxRepository.isProcessed(any())).thenReturn(false);
        when(accountRepository.findByAccountRef(from.getAccountRef())).thenReturn(Optional.of(from));
        when(accountRepository.findByAccountRef(to.getAccountRef())).thenReturn(Optional.of(to));
        when(accountRepository.lockById(id1)).thenReturn(Optional.of(from));
        when(accountRepository.lockById(id2)).thenReturn(Optional.of(to));

        service.process(ev);

        InOrder inOrder = inOrder(accountRepository);
        inOrder.verify(accountRepository).lockById(expectedOrder.get(0));
        inOrder.verify(accountRepository).lockById(expectedOrder.get(1));
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void process_sufficientFunds_debitsCreditsAndEnqueuesCompleted() {
        UUID fromId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID toId   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        Account from = account(fromId, 1000L);
        Account to   = account(toId, 0L);
        TransferRequestedEvent ev = event(from.getAccountRef(), to.getAccountRef(), 400L);

        when(inboxRepository.isProcessed(any())).thenReturn(false);
        when(accountRepository.findByAccountRef(from.getAccountRef())).thenReturn(Optional.of(from));
        when(accountRepository.findByAccountRef(to.getAccountRef())).thenReturn(Optional.of(to));
        when(accountRepository.lockById(fromId)).thenReturn(Optional.of(from));
        when(accountRepository.lockById(toId)).thenReturn(Optional.of(to));

        service.process(ev);

        // balances mutated on domain objects
        assertThat(from.getBalance()).isEqualTo(600L);
        assertThat(to.getBalance()).isEqualTo(400L);

        // both accounts saved
        verify(accountRepository).save(from);
        verify(accountRepository).save(to);

        // two ledger entries: DEBIT + CREDIT
        ArgumentCaptor<LedgerEntry> ledgerCap = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository, times(2)).save(ledgerCap.capture());
        List<LedgerEntry> entries = ledgerCap.getAllValues();
        assertThat(entries).anySatisfy(e -> {
            assertThat(e.getDirection()).isEqualTo(LedgerEntry.DIR_DEBIT);
            assertThat(e.getAccountId()).isEqualTo(fromId);
        });
        assertThat(entries).anySatisfy(e -> {
            assertThat(e.getDirection()).isEqualTo(LedgerEntry.DIR_CREDIT);
            assertThat(e.getAccountId()).isEqualTo(toId);
        });

        // COMPLETED result enqueued
        ArgumentCaptor<TransferResult> resultCap = ArgumentCaptor.forClass(TransferResult.class);
        verify(outboxRepository).enqueue(resultCap.capture());
        assertThat(resultCap.getValue().status()).isEqualTo(TransferResult.STATUS_COMPLETED);

        verify(inboxRepository).markProcessed(ev.messageId(), ev.transferId());
    }

    // ── insufficient funds ────────────────────────────────────────────────────

    @Test
    void process_insufficientFunds_enqueuesFailedAndDoesNotWriteLedger() {
        UUID fromId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID toId   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        Account from = account(fromId, 50L);
        Account to   = account(toId, 0L);
        TransferRequestedEvent ev = event(from.getAccountRef(), to.getAccountRef(), 200L);

        when(inboxRepository.isProcessed(any())).thenReturn(false);
        when(accountRepository.findByAccountRef(from.getAccountRef())).thenReturn(Optional.of(from));
        when(accountRepository.findByAccountRef(to.getAccountRef())).thenReturn(Optional.of(to));
        when(accountRepository.lockById(fromId)).thenReturn(Optional.of(from));
        when(accountRepository.lockById(toId)).thenReturn(Optional.of(to));

        service.process(ev);

        ArgumentCaptor<TransferResult> cap = ArgumentCaptor.forClass(TransferResult.class);
        verify(outboxRepository).enqueue(cap.capture());
        assertThat(cap.getValue().status()).isEqualTo(TransferResult.STATUS_FAILED);
        assertThat(cap.getValue().reason()).isEqualTo(TransferResult.REASON_INSUFFICIENT);

        verifyNoInteractions(ledgerRepository);
        verify(inboxRepository).markProcessed(ev.messageId(), ev.transferId());
        // no balance change
        assertThat(from.getBalance()).isEqualTo(50L);
    }

    // ── account disappears during lock ────────────────────────────────────────

    @Test
    void process_accountDisappearsOnLock_throwsIllegalStateException() {
        UUID fromId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID toId   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        Account from = account(fromId, 1000L);
        Account to   = account(toId, 0L);
        TransferRequestedEvent ev = event(from.getAccountRef(), to.getAccountRef(), 100L);

        when(inboxRepository.isProcessed(any())).thenReturn(false);
        when(accountRepository.findByAccountRef(from.getAccountRef())).thenReturn(Optional.of(from));
        when(accountRepository.findByAccountRef(to.getAccountRef())).thenReturn(Optional.of(to));
        // simulate one of them vanishing during lock
        when(accountRepository.lockById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(ev))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Account disappeared during lock");
    }
}
