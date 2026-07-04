package com.fasttrans.transfer.application.services;

import com.fasttrans.transfer.application.dto.CreateTransferRequest;
import com.fasttrans.transfer.application.dto.TransferResponse;
import com.fasttrans.transfer.application.dto.TransferResultEvent;
import com.fasttrans.transfer.domain.entities.Transfer;
import com.fasttrans.transfer.domain.entities.TransferStatus;
import com.fasttrans.transfer.domain.exception.DuplicateIdempotencyException;
import com.fasttrans.transfer.domain.exception.OwnershipDeniedException;
import com.fasttrans.transfer.domain.exception.TransferNotFoundException;
import com.fasttrans.transfer.domain.interfaces.AccountClient;
import com.fasttrans.transfer.domain.interfaces.InboxRepository;
import com.fasttrans.transfer.domain.interfaces.OutboxRepository;
import com.fasttrans.transfer.domain.interfaces.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock private TransferRepository transferRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private InboxRepository inboxRepository;
    @Mock private AccountClient accountClient;

    @InjectMocks
    private TransferService transferService;

    private UUID userId;
    private String idempotencyKey;
    private CreateTransferRequest req;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        idempotencyKey = "idem-key-1";
        req = new CreateTransferRequest("ACC001", "ACC002", 100_000L, "VND");
    }

    // ---- create: ownership denied ----

    @Test
    void create_throws_ownership_denied_when_account_not_owned() {
        when(accountClient.validateOwnership(userId.toString(), "ACC001")).thenReturn(false);

        assertThatThrownBy(() -> transferService.create(userId, idempotencyKey, req))
                .isInstanceOf(OwnershipDeniedException.class)
                .hasMessageContaining("ACC001");

        verifyNoInteractions(transferRepository, outboxRepository, inboxRepository);
    }

    // ---- create: idempotency hit ----

    @Test
    void create_returns_existing_transfer_on_idempotency_hit() {
        Transfer existing = makeTransfer(userId, idempotencyKey, TransferStatus.PENDING);
        when(accountClient.validateOwnership(userId.toString(), "ACC001")).thenReturn(true);
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.of(existing));

        TransferResponse response = transferService.create(userId, idempotencyKey, req);

        assertThat(response.id()).isEqualTo(existing.getId().toString());
        assertThat(response.status()).isEqualTo("PENDING");
        verifyNoInteractions(outboxRepository);
        verify(transferRepository, never()).save(any());
    }

    // ---- create: happy path ----

    @Test
    void create_happy_path_saves_transfer_and_enqueues_outbox() {
        when(accountClient.validateOwnership(userId.toString(), "ACC001")).thenReturn(true);
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.empty());
        doNothing().when(transferRepository).save(any());
        doNothing().when(outboxRepository).enqueueTransferRequested(any());

        TransferResponse response = transferService.create(userId, idempotencyKey, req);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.fromAccountRef()).isEqualTo("ACC001");
        verify(transferRepository).save(any(Transfer.class));
        verify(outboxRepository).enqueueTransferRequested(any(Transfer.class));
    }

    // ---- create: race re-read returns existing ----

    @Test
    void create_race_condition_rereads_when_duplicate_exception_thrown() {
        Transfer racedTransfer = makeTransfer(userId, idempotencyKey, TransferStatus.PENDING);
        when(accountClient.validateOwnership(userId.toString(), "ACC001")).thenReturn(true);
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.empty())                     // first check: empty
                .thenReturn(Optional.of(racedTransfer));          // re-read after race
        doThrow(new DuplicateIdempotencyException("conflict", new RuntimeException()))
                .when(transferRepository).save(any());

        TransferResponse response = transferService.create(userId, idempotencyKey, req);

        assertThat(response.id()).isEqualTo(racedTransfer.getId().toString());
        verify(transferRepository, times(2))
                .findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    // ---- create: race re-read returns empty -> RuntimeException ----

    @Test
    void create_race_condition_throws_runtime_when_reread_empty() {
        when(accountClient.validateOwnership(userId.toString(), "ACC001")).thenReturn(true);
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                .thenReturn(Optional.empty());
        doThrow(new DuplicateIdempotencyException("conflict", new RuntimeException()))
                .when(transferRepository).save(any());

        assertThatThrownBy(() -> transferService.create(userId, idempotencyKey, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("re-read failed");
    }

    // ---- list ----

    @Test
    void list_returns_mapped_responses() {
        Transfer t1 = makeTransfer(userId, "k1", TransferStatus.PENDING);
        Transfer t2 = makeTransfer(userId, "k2", TransferStatus.COMPLETED);
        when(transferRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(t1, t2));

        List<TransferResponse> result = transferService.list(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).status()).isEqualTo("PENDING");
        assertThat(result.get(1).status()).isEqualTo("COMPLETED");
    }

    @Test
    void list_returns_empty_when_no_transfers() {
        when(transferRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());

        assertThat(transferService.list(userId)).isEmpty();
    }

    // ---- detail ----

    @Test
    void detail_returns_response_when_transfer_found() {
        UUID transferId = UUID.randomUUID();
        Transfer t = makeTransfer(userId, idempotencyKey, TransferStatus.COMPLETED);
        when(transferRepository.findByIdAndUserId(transferId, userId)).thenReturn(Optional.of(t));

        TransferResponse response = transferService.detail(transferId, userId);

        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @Test
    void detail_throws_not_found_when_transfer_missing() {
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findByIdAndUserId(transferId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.detail(transferId, userId))
                .isInstanceOf(TransferNotFoundException.class)
                .hasMessageContaining(transferId.toString());
    }

    // ---- applyResult: inbox already processed ----

    @Test
    void applyResult_skips_when_message_already_processed() {
        UUID messageId = UUID.randomUUID();
        TransferResultEvent event = makeEvent(messageId, UUID.randomUUID(), "COMPLETED", null);
        when(inboxRepository.isProcessed(messageId)).thenReturn(true);

        transferService.applyResult(event);

        verifyNoInteractions(transferRepository, outboxRepository);
        verify(inboxRepository, never()).markProcessed(any());
    }

    // ---- applyResult: PENDING transfer -> update + markProcessed ----

    @Test
    void applyResult_updates_pending_transfer_and_marks_processed() {
        UUID messageId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        Transfer pending = makeTransfer(userId, idempotencyKey, TransferStatus.PENDING);
        pending.setId(transferId);

        TransferResultEvent event = makeEvent(messageId, transferId, "COMPLETED", null);
        when(inboxRepository.isProcessed(messageId)).thenReturn(false);
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(pending));

        transferService.applyResult(event);

        assertThat(pending.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        verify(transferRepository).save(pending);
        verify(inboxRepository).markProcessed(messageId);
    }

    // ---- applyResult: non-PENDING transfer -> only markProcessed ----

    @Test
    void applyResult_skips_update_when_transfer_not_pending() {
        UUID messageId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        Transfer completed = makeTransfer(userId, idempotencyKey, TransferStatus.COMPLETED);
        completed.setId(transferId);

        TransferResultEvent event = makeEvent(messageId, transferId, "COMPLETED", null);
        when(inboxRepository.isProcessed(messageId)).thenReturn(false);
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(completed));

        transferService.applyResult(event);

        verify(transferRepository, never()).save(any());
        verify(inboxRepository).markProcessed(messageId);
    }

    // ---- applyResult: transfer not found -> still markProcessed ----

    @Test
    void applyResult_marks_processed_even_when_transfer_not_found() {
        UUID messageId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        TransferResultEvent event = makeEvent(messageId, transferId, "COMPLETED", null);

        when(inboxRepository.isProcessed(messageId)).thenReturn(false);
        when(transferRepository.findById(transferId)).thenReturn(Optional.empty());

        transferService.applyResult(event);

        verify(transferRepository, never()).save(any());
        verify(inboxRepository).markProcessed(messageId);
    }

    // ---- applyResult: FAILED with reason ----

    @Test
    void applyResult_applies_failed_with_reason() {
        UUID messageId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        Transfer pending = makeTransfer(userId, idempotencyKey, TransferStatus.PENDING);
        pending.setId(transferId);

        TransferResultEvent event = makeEvent(messageId, transferId, "FAILED", "INSUFFICIENT_FUNDS");
        when(inboxRepository.isProcessed(messageId)).thenReturn(false);
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(pending));

        transferService.applyResult(event);

        assertThat(pending.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(pending.getReason()).isEqualTo("INSUFFICIENT_FUNDS");
        verify(inboxRepository).markProcessed(messageId);
    }

    // ---- helpers ----

    private Transfer makeTransfer(UUID userId, String key, TransferStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Transfer(UUID.randomUUID(), userId, key, "ACC001", "ACC002",
                100_000L, "VND", status, null, now, now);
    }

    private TransferResultEvent makeEvent(UUID messageId, UUID transferId, String status, String reason) {
        return new TransferResultEvent(
                messageId.toString(),
                transferId.toString(),
                status,
                reason,
                OffsetDateTime.now().toString()
        );
    }
}
