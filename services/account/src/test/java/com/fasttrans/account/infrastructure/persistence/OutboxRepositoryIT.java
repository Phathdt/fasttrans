package com.fasttrans.account.infrastructure.persistence;

import com.fasttrans.account.domain.entities.TransferResult;
import com.fasttrans.account.support.AbstractPostgresIT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OutboxRepositoryIT extends AbstractPostgresIT {

    @Autowired SpringDataOutboxRepository jpaRepo;

    private OutboxRepositoryImpl buildRepo() {
        return new OutboxRepositoryImpl(jpaRepo,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void lockPendingBatch_returnsPendingEntriesUpToLimit() {
        OutboxRepositoryImpl repo = buildRepo();
        // Enqueue 3 results
        for (int i = 0; i < 3; i++) {
            repo.enqueue(TransferResult.completed(UUID.randomUUID()));
        }

        List<OutboxJpaEntity> batch = jpaRepo.lockPendingBatch();

        assertThat(batch).hasSize(3);
        assertThat(batch).allMatch(e -> "PENDING".equals(e.getStatus()));
    }

    @Test
    void lockPendingBatch_afterMarkingSent_doesNotReturnSentEntries() {
        OutboxRepositoryImpl repo = buildRepo();
        repo.enqueue(TransferResult.completed(UUID.randomUUID()));

        List<OutboxJpaEntity> batch = jpaRepo.lockPendingBatch();
        assertThat(batch).hasSize(1);

        // Mark as sent
        OutboxJpaEntity entry = batch.get(0);
        entry.markSent();
        jpaRepo.save(entry);

        List<OutboxJpaEntity> afterSent = jpaRepo.lockPendingBatch();
        assertThat(afterSent).isEmpty();
    }

    @Test
    void enqueue_completedResult_persistsWithCorrectTopic() {
        OutboxRepositoryImpl repo = buildRepo();
        UUID transferId = UUID.randomUUID();

        repo.enqueue(TransferResult.completed(transferId));

        List<OutboxJpaEntity> all = jpaRepo.findAll();
        OutboxJpaEntity saved = all.stream()
                .filter(e -> e.getMsgKey().equals(transferId.toString()))
                .findFirst()
                .orElseThrow();

        assertThat(saved.getTopic()).isEqualTo("transfer.result");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getPayload()).contains(transferId.toString());
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
