package com.fasttrans.account.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasttrans.account.dto.TransferRequestedEvent;
import com.fasttrans.account.dto.TransferResultEvent;
import com.fasttrans.account.entity.*;
import com.fasttrans.account.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Core business logic — processes transfer.requested.
 * Invariant: ledger + balance + processed_messages + outbox live in 1 transaction.
 * Deadlock avoidance: lock the 2 accounts in sorted UUID order.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private static final String TOPIC_RESULT = "transfer.result";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String REASON_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    private static final String REASON_INSUFFICIENT = "INSUFFICIENT_FUNDS";
    private static final String DIR_DEBIT = "DEBIT";
    private static final String DIR_CREDIT = "CREDIT";

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AccountService(AccountRepository accountRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          ProcessedMessageRepository processedMessageRepository,
                          OutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a single transfer.requested event.
     * Idempotent: messageId already in processed_messages → skip.
     */
    @Transactional
    public void process(TransferRequestedEvent event) {
        // 1. Inbox dedup — check whether messageId was already processed
        if (processedMessageRepository.existsById(event.messageId())) {
            log.info("Duplicate message ignored messageId={} transferId={}",
                    event.messageId(), event.transferId());
            return;
        }

        // 2. Resolve accountRef → entity (no lock, just to get the UUID)
        var fromOpt = accountRepository.findByAccountRef(event.fromAccountRef());
        var toOpt = accountRepository.findByAccountRef(event.toAccountRef());

        if (fromOpt.isEmpty() || toOpt.isEmpty()) {
            String missing = fromOpt.isEmpty() ? event.fromAccountRef() : event.toAccountRef();
            log.warn("Account not found ref={} transferId={}", missing, event.transferId());
            writeOutbox(event.transferId(), STATUS_FAILED, REASON_NOT_FOUND);
            insertProcessed(event.messageId(), event.transferId());
            return;
        }

        UUID fromId = fromOpt.get().getId();
        UUID toId = toOpt.get().getId();

        // 3. Lock the 2 accounts in sorted UUID order — avoid deadlock
        // Use a LinkedHashMap to keep the lockById results in sorted order.
        List<UUID> sortedIds = List.of(fromId, toId).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        // lockById triggers SELECT FOR UPDATE — call once per account, keep the result.
        java.util.Map<UUID, AccountEntity> locked = new java.util.LinkedHashMap<>();
        for (UUID id : sortedIds) {
            locked.put(id, accountRepository.lockById(id)
                    .orElseThrow(() -> new IllegalStateException("Account disappeared during lock: " + id)));
        }

        AccountEntity from = locked.get(fromId);
        AccountEntity to   = locked.get(toId);

        // 4. Check the balance
        if (from.getBalance() < event.amount()) {
            log.warn("Insufficient funds accountId={} balance={} amount={} transferId={}",
                    fromId, from.getBalance(), event.amount(), event.transferId());
            writeOutbox(event.transferId(), STATUS_FAILED, REASON_INSUFFICIENT);
            insertProcessed(event.messageId(), event.transferId());
            return;
        }

        // 5. Write ledger DEBIT(from) + CREDIT(to), update both balances
        from.adjustBalance(-event.amount());
        to.adjustBalance(event.amount());

        accountRepository.save(from);
        accountRepository.save(to);

        LedgerEntryEntity debit = new LedgerEntryEntity();
        debit.setId(UUID.randomUUID());
        debit.setAccountId(fromId);
        debit.setTransferId(event.transferId());
        debit.setDirection(DIR_DEBIT);
        debit.setAmount(event.amount());
        debit.setBalanceAfter(from.getBalance());
        ledgerEntryRepository.save(debit);

        LedgerEntryEntity credit = new LedgerEntryEntity();
        credit.setId(UUID.randomUUID());
        credit.setAccountId(toId);
        credit.setTransferId(event.transferId());
        credit.setDirection(DIR_CREDIT);
        credit.setAmount(event.amount());
        credit.setBalanceAfter(to.getBalance());
        ledgerEntryRepository.save(credit);

        // 6. Outbox result COMPLETED + processed_messages — same transaction
        writeOutbox(event.transferId(), STATUS_COMPLETED, null);
        insertProcessed(event.messageId(), event.transferId());

        log.info("Transfer processed COMPLETED transferId={} from={} to={} amount={}",
                event.transferId(), fromId, toId, event.amount());
    }

    // --- helpers ---

    private void writeOutbox(UUID transferId, String status, String reason) {
        TransferResultEvent result = new TransferResultEvent(
                UUID.randomUUID(),
                transferId,
                status,
                reason,
                Instant.now()
        );
        String payload;
        try {
            payload = objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize TransferResultEvent", e);
        }
        OutboxEntity outbox = OutboxEntity.pending(
                transferId,
                TOPIC_RESULT,
                transferId.toString(),  // msg_key = transferId (partition key)
                payload
        );
        outboxRepository.save(outbox);
    }

    private void insertProcessed(UUID messageId, UUID transferId) {
        ProcessedMessageEntity pm = new ProcessedMessageEntity();
        pm.setMessageId(messageId);
        pm.setTransferId(transferId);
        processedMessageRepository.save(pm);
    }
}
