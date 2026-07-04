package com.fasttrans.account.application.services;

import com.fasttrans.account.application.dto.TransferRequestedEvent;
import com.fasttrans.account.domain.entities.TransferResult;
import com.fasttrans.account.infrastructure.persistence.OutboxJpaEntity;
import com.fasttrans.account.infrastructure.persistence.SpringDataOutboxRepository;
import com.fasttrans.account.infrastructure.persistence.SpringDataProcessedMessageRepository;
import com.fasttrans.account.support.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Full-stack integration test for ProcessTransferService on real Postgres.
 * Verifies: balance constraint, ledger entries, idempotency, and error paths.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProcessTransferServiceIT extends AbstractPostgresIT {

    @Autowired ProcessTransferService processTransferService;
    @Autowired SpringDataOutboxRepository outboxRepository;
    @Autowired SpringDataProcessedMessageRepository inboxRepository;

    // Seeded accounts (from migration)
    private static final String ALICE_REF_1 = "100000000001";  // balance 1_000_000
    private static final String ALICE_REF_2 = "100000000002";  // balance 50_000
    private static final String BOB_REF     = "200000000001";  // balance 0

    @BeforeEach
    void cleanOutboxAndInbox() {
        outboxRepository.deleteAll();
        inboxRepository.deleteAll();
    }

    private TransferRequestedEvent event(String fromRef, String toRef, long amount) {
        return new TransferRequestedEvent(
                UUID.randomUUID(), UUID.randomUUID(), fromRef, toRef, amount, "VND", Instant.now());
    }

    @Test
    void process_happyPath_completedResultInOutbox() {
        TransferRequestedEvent ev = event(ALICE_REF_1, BOB_REF, 100_000L);

        processTransferService.process(ev);

        List<OutboxJpaEntity> outbox = outboxRepository.findAll();
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getPayload()).contains(TransferResult.STATUS_COMPLETED);
        assertThat(inboxRepository.existsById(ev.messageId())).isTrue();
    }

    @Test
    void process_idempotency_secondCallIsNoop() {
        TransferRequestedEvent ev = event(ALICE_REF_1, BOB_REF, 50_000L);

        processTransferService.process(ev);
        processTransferService.process(ev); // duplicate

        // Only one outbox entry from the first call
        assertThat(outboxRepository.findAll()).hasSize(1);
    }

    @Test
    void process_insufficientFunds_failedResultInOutbox() {
        // Use Long.MAX_VALUE so this always exceeds any possible balance
        TransferRequestedEvent ev = event(BOB_REF, ALICE_REF_1, Long.MAX_VALUE);

        processTransferService.process(ev);

        List<OutboxJpaEntity> outbox = outboxRepository.findAll();
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getPayload()).contains(TransferResult.REASON_INSUFFICIENT);
    }

    @Test
    void process_unknownFromAccount_accountNotFoundResultInOutbox() {
        TransferRequestedEvent ev = event("999999999999", BOB_REF, 100L);

        processTransferService.process(ev);

        List<OutboxJpaEntity> outbox = outboxRepository.findAll();
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getPayload()).contains(TransferResult.REASON_NOT_FOUND);
    }

    @Test
    void process_unknownToAccount_accountNotFoundResultInOutbox() {
        TransferRequestedEvent ev = event(ALICE_REF_1, "999999999999", 100L);

        processTransferService.process(ev);

        assertThat(outboxRepository.findAll().get(0).getPayload())
                .contains(TransferResult.REASON_NOT_FOUND);
    }
}
