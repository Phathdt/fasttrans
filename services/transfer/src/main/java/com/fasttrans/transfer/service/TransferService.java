package com.fasttrans.transfer.service;

import com.fasttrans.transfer.dto.AccountResponse;
import com.fasttrans.transfer.dto.CreateTransferRequest;
import com.fasttrans.transfer.dto.TransferRequestedEvent;
import com.fasttrans.transfer.dto.TransferResponse;
import com.fasttrans.transfer.dto.TransferResultEvent;
import com.fasttrans.transfer.entity.OutboxEntity;
import com.fasttrans.transfer.entity.ProcessedMessageEntity;
import com.fasttrans.transfer.entity.TransferEntity;
import com.fasttrans.transfer.grpc.AccountGrpcClient;
import com.fasttrans.transfer.repository.OutboxRepository;
import com.fasttrans.transfer.repository.ProcessedMessageRepository;
import com.fasttrans.transfer.repository.TransferRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.StatusRuntimeException;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final AccountGrpcClient accountGrpcClient;
    private final ObjectMapper objectMapper;

    public TransferService(TransferRepository transferRepository,
                           OutboxRepository outboxRepository,
                           ProcessedMessageRepository processedMessageRepository,
                           AccountGrpcClient accountGrpcClient,
                           ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.accountGrpcClient = accountGrpcClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new transfer or return the existing one if the idempotency key matches.
     * Calls gRPC validateOwnership first; if the account service is down → 503; if not owned → 403.
     */
    public TransferResponse create(UUID userId, String idempotencyKey, CreateTransferRequest req) {
        // 1. Validate ownership via gRPC (before opening the write transaction)
        boolean owned;
        try {
            owned = accountGrpcClient.validateOwnership(userId.toString(), req.fromAccountRef());
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                throw new AccountUnavailableException("Account service unavailable", e);
            }
            throw new AccountUnavailableException("gRPC error: " + e.getStatus(), e);
        }

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
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread inserted the same (userId, idempotencyKey) concurrently → re-read
            log.warn("Unique conflict on uq_transfers_user_idem; re-reading idempotency key={}", idempotencyKey);
            return transferRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .map(TransferResponse::from)
                    .orElseThrow(() -> new RuntimeException("Conflict while creating transfer but re-read failed", e));
        }
    }

    @Transactional
    protected TransferResponse createInTransaction(UUID userId, String idempotencyKey,
                                                    CreateTransferRequest req) {
        UUID transferId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        String requestedAt = Instant.now().toString();

        // Create the PENDING transfer entity
        TransferEntity transfer = TransferEntity.pending(
                transferId, userId, idempotencyKey,
                req.fromAccountRef(), req.toAccountRef(),
                req.amount(), req.currency());
        transferRepository.save(transfer);

        // Serialize the event payload → JSON string (the outbox payload is jsonb)
        TransferRequestedEvent event = new TransferRequestedEvent(
                messageId.toString(),
                transferId.toString(),
                req.fromAccountRef(),
                req.toAccountRef(),
                req.amount(),
                req.currency(),
                requestedAt
        );
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to serialize TransferRequestedEvent", e);
        }

        // Write the outbox row in the same transaction
        OutboxEntity outbox = OutboxEntity.pending(
                UUID.randomUUID(), transferId,
                "transfer.requested", req.fromAccountRef(), payload);
        outboxRepository.save(outbox);

        log.info("Transfer created successfully transferId={} messageId={}", transferId, messageId);
        return TransferResponse.from(transfer);
    }

    /**
     * Proxy gRPC ListAccounts → the user's list of accounts.
     */
    public List<AccountResponse> listAccounts(UUID userId) {
        try {
            var response = accountGrpcClient.listAccounts(userId.toString());
            return response.getAccountsList().stream()
                    .map(a -> new AccountResponse(
                            a.getAccountRef(),
                            a.getOwnerName(),
                            a.getBalance(),
                            a.getCurrency()))
                    .toList();
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                throw new AccountUnavailableException("Account service unavailable", e);
            }
            throw new AccountUnavailableException("gRPC error: " + e.getStatus(), e);
        }
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
        if (processedMessageRepository.existsById(messageId)) {
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
        processedMessageRepository.save(new ProcessedMessageEntity(messageId));
    }
}
