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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core business logic — processes transfer.requested.
 * Invariant: ledger + balance + processed_messages + outbox live in 1 transaction.
 * Deadlock avoidance: lock the 2 accounts in sorted UUID order.
 */
@Service
public class ProcessTransferService {

    private static final Logger log = LoggerFactory.getLogger(ProcessTransferService.class);

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final InboxRepository inboxRepository;
    private final OutboxRepository outboxRepository;

    public ProcessTransferService(AccountRepository accountRepository,
                                  LedgerRepository ledgerRepository,
                                  InboxRepository inboxRepository,
                                  OutboxRepository outboxRepository) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.inboxRepository = inboxRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Processes a single transfer.requested event.
     * Idempotent: messageId already in processed_messages → skip.
     */
    @Transactional
    public void process(TransferRequestedEvent event) {
        // 1. Inbox dedup — check whether messageId was already processed
        if (inboxRepository.isProcessed(event.messageId())) {
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
            outboxRepository.enqueue(TransferResult.accountNotFound(event.transferId()));
            inboxRepository.markProcessed(event.messageId(), event.transferId());
            return;
        }

        UUID fromId = fromOpt.get().getId();
        UUID toId = toOpt.get().getId();

        // 3. Lock the 2 accounts in sorted UUID order — avoid deadlock
        List<UUID> sortedIds = List.of(fromId, toId).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        // lockById triggers SELECT FOR UPDATE — call once per account, keep the result.
        Map<UUID, Account> locked = new LinkedHashMap<>();
        for (UUID id : sortedIds) {
            locked.put(id, accountRepository.lockById(id)
                    .orElseThrow(() -> new IllegalStateException("Account disappeared during lock: " + id)));
        }

        Account from = locked.get(fromId);
        Account to   = locked.get(toId);

        // 4. Check the balance + debit — domain raises InsufficientFundsException
        try {
            from.debit(event.amount());
        } catch (InsufficientFundsException e) {
            log.warn("Insufficient funds accountId={} balance={} amount={} transferId={}",
                    fromId, e.getBalance(), event.amount(), event.transferId());
            outboxRepository.enqueue(TransferResult.insufficientFunds(event.transferId()));
            inboxRepository.markProcessed(event.messageId(), event.transferId());
            return;
        }
        to.credit(event.amount());

        // 5. Write ledger DEBIT(from) + CREDIT(to), update both balances
        accountRepository.save(from);
        accountRepository.save(to);

        ledgerRepository.save(LedgerEntry.debit(fromId, event.transferId(), event.amount(), from.getBalance()));
        ledgerRepository.save(LedgerEntry.credit(toId, event.transferId(), event.amount(), to.getBalance()));

        // 6. Outbox result COMPLETED + processed_messages — same transaction
        outboxRepository.enqueue(TransferResult.completed(event.transferId()));
        inboxRepository.markProcessed(event.messageId(), event.transferId());

        log.info("Transfer processed COMPLETED transferId={} from={} to={} amount={}",
                event.transferId(), fromId, toId, event.amount());
    }
}
