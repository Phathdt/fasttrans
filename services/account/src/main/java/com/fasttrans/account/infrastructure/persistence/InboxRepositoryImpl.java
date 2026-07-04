package com.fasttrans.account.infrastructure.persistence;

import com.fasttrans.account.domain.interfaces.InboxRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** JPA-backed implementation of the InboxRepository domain contract (processed_messages). */
@Repository
public class InboxRepositoryImpl implements InboxRepository {

    private final SpringDataProcessedMessageRepository jpa;

    public InboxRepositoryImpl(SpringDataProcessedMessageRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean isProcessed(UUID messageId) {
        return jpa.existsById(messageId);
    }

    @Override
    public void markProcessed(UUID messageId, UUID transferId) {
        ProcessedMessageJpaEntity pm = new ProcessedMessageJpaEntity();
        pm.setMessageId(messageId);
        pm.setTransferId(transferId);
        jpa.save(pm);
    }
}
