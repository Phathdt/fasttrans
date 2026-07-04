package com.fasttrans.transfer.application.services;

import com.fasttrans.transfer.application.dto.AccountResponse;
import com.fasttrans.transfer.application.dto.CreateTransferRequest;
import com.fasttrans.transfer.application.dto.TransferResponse;
import com.fasttrans.transfer.application.dto.TransferResultEvent;
import com.fasttrans.transfer.domain.entities.Transfer;
import com.fasttrans.transfer.domain.exception.DuplicateIdempotencyException;
import com.fasttrans.transfer.domain.exception.OwnershipDeniedException;
import com.fasttrans.transfer.domain.exception.TransferNotFoundException;
import com.fasttrans.transfer.domain.interfaces.AccountClient;
import com.fasttrans.transfer.domain.interfaces.InboxRepository;
import com.fasttrans.transfer.domain.interfaces.OutboxRepository;
import com.fasttrans.transfer.domain.interfaces.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;
    private final InboxRepository inboxRepository;
    private final AccountClient accountClient;

    public TransferService(TransferRepository transferRepository,
                           OutboxRepository outboxRepository,
                           InboxRepository inboxRepository,
                           AccountClient accountClient) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.inboxRepository = inboxRepository;
        this.accountClient = accountClient;
    }

    /**
     * Create a new transfer or return the existing one if the idempotency key matches.
     * Calls gRPC validateOwnership first; if the account service is down → 503; if not owned → 403.
     */
    public TransferResponse create(UUID userId, String idempotencyKey, CreateTransferRequest req) {
        // 1. Validate ownership via gRPC (before opening the write transaction).
        //    Transport failures are already mapped to AccountUnavailableException in the client.
        boolean owned = accountClient.validateOwnership(userId.toString(), req.fromAccountRef());

        if (!owned) {
            throw new OwnershipDeniedException(
                    "Account " + req.fromAccountRef() + " does not belong to user " + userId);
        }

        // 2. Idempotency check before opening the transaction — on hit → return the existing one, do not create
        var existing = transferRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return TransferResponse.from(existing.get());
        }

        // 3. Create in a transaction; catch the unique race
        try {
            return createInTransaction(userId, idempotencyKey, req);
        } catch (DuplicateIdempotencyException e) {
            // Race condition: another thread inserted the same (userId, idempotencyKey) concurrently → re-read
            log.warn("Unique conflict on uq_transfers_user_idem; re-reading idempotency key={}", idempotencyKey);
            return transferRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .map(TransferResponse::from)
                    .orElseThrow(() -> new RuntimeException("Conflict while creating transfer but re-read failed", e));
        }
    }

    @Transactional
    public TransferResponse createInTransaction(UUID userId, String idempotencyKey,
                                                CreateTransferRequest req) {
        UUID transferId = UUID.randomUUID();

        // Create the PENDING transfer
        Transfer transfer = Transfer.pending(
                transferId, userId, idempotencyKey,
                req.fromAccountRef(), req.toAccountRef(),
                req.amount(), req.currency());
        transferRepository.save(transfer);

        // Enqueue the transfer.requested event in the same transaction (outbox serializes the payload)
        outboxRepository.enqueueTransferRequested(transfer);

        log.info("Transfer created successfully transferId={}", transferId);
        return TransferResponse.from(transfer);
    }

    /**
     * Proxy gRPC ListAccounts → the user's list of accounts.
     */
    public List<AccountResponse> listAccounts(UUID userId) {
        return accountClient.listAccounts(userId.toString()).stream()
                .map(a -> new AccountResponse(
                        a.accountRef(),
                        a.ownerName(),
                        a.balance(),
                        a.currency()))
                .toList();
    }

    /**
     * The user's transfers, most recent first.
     */
    public List<TransferResponse> list(UUID userId) {
        return transferRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(TransferResponse::from)
                .toList();
    }

    /**
     * Details of a single transfer; 404 if it does not exist or does not belong to the user.
     */
    public TransferResponse detail(UUID id, UUID userId) {
        return transferRepository.findByIdAndUserId(id, userId)
                .map(TransferResponse::from)
                .orElseThrow(() -> new TransferNotFoundException(
                        "Transfer " + id + " not found or does not belong to user " + userId));
    }

    /**
     * Consume transfer.result: dedup via processed_messages; only update when PENDING.
     */
    @Transactional
    public void applyResult(TransferResultEvent event) {
        UUID messageId = UUID.fromString(event.messageId());

        // Inbox dedup: messageId already processed → skip
        if (inboxRepository.isProcessed(messageId)) {
            log.info("Skipping already-processed messageId: {}", messageId);
            return;
        }

        // Load the transfer and update it if still PENDING
        if (event.transferId() != null) {
            UUID transferId = UUID.fromString(event.transferId());
            transferRepository.findById(transferId).ifPresentOrElse(transfer -> {
                if (transfer.isPending()) {
                    transfer.applyResult(event.status(), event.reason());
                    transferRepository.save(transfer);
                    log.info("Transfer {} → {} (reason={})", transferId, event.status(), event.reason());
                } else {
                    log.warn("Transfer {} is no longer PENDING, skipping update status={}", transferId, event.status());
                }
            }, () -> log.warn("Transfer {} not found for messageId={}", transferId, messageId));
        }

        // Always write to processed_messages to block replay even if the transfer was not found
        inboxRepository.markProcessed(messageId);
    }
}
