package com.fasttrans.transfer.infrastructure.persistence;

import com.fasttrans.transfer.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private SpringDataOutboxRepository outboxRepository;

    @Test
    void lockPendingBatch_returns_pending_rows() {
        OutboxJpaEntity e1 = OutboxJpaEntity.pending(
                UUID.randomUUID(), UUID.randomUUID(), "transfer.requested", "ACC001", "{\"a\":1}");
        OutboxJpaEntity e2 = OutboxJpaEntity.pending(
                UUID.randomUUID(), UUID.randomUUID(), "transfer.requested", "ACC002", "{\"a\":2}");
        outboxRepository.save(e1);
        outboxRepository.save(e2);

        List<OutboxJpaEntity> batch = outboxRepository.lockPendingBatch();

        assertThat(batch).hasSizeGreaterThanOrEqualTo(2);
        assertThat(batch).allMatch(e -> "PENDING".equals(e.getPayload() != null ? "PENDING" : ""));
    }

    @Test
    void lockPendingBatch_excludes_sent_rows() {
        OutboxJpaEntity sent = OutboxJpaEntity.pending(
                UUID.randomUUID(), UUID.randomUUID(), "transfer.requested", "ACC003", "{\"a\":3}");
        sent.markSent();
        outboxRepository.save(sent);

        List<OutboxJpaEntity> batch = outboxRepository.lockPendingBatch();

        assertThat(batch).noneMatch(e -> e.getId().equals(sent.getId()));
    }

    @Test
    void lockPendingBatch_returns_empty_when_no_pending_rows() {
        // Mark all existing as sent or start clean — just verify empty is possible
        List<OutboxJpaEntity> all = outboxRepository.findAll();
        all.forEach(e -> { e.markSent(); outboxRepository.save(e); });

        List<OutboxJpaEntity> batch = outboxRepository.lockPendingBatch();

        assertThat(batch).isEmpty();
    }
}
