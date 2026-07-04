package com.fasttrans.account.infrastructure.persistence;

import com.fasttrans.account.domain.interfaces.InboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboxRepositoryImplTest {

    @Mock SpringDataProcessedMessageRepository jpa;

    InboxRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InboxRepositoryImpl(jpa);
    }

    @Test
    void isProcessed_existingMessage_returnsTrue() {
        UUID messageId = UUID.randomUUID();
        when(jpa.existsById(messageId)).thenReturn(true);

        assertThat(repo.isProcessed(messageId)).isTrue();
    }

    @Test
    void isProcessed_unknownMessage_returnsFalse() {
        UUID messageId = UUID.randomUUID();
        when(jpa.existsById(messageId)).thenReturn(false);

        assertThat(repo.isProcessed(messageId)).isFalse();
    }

    @Test
    void markProcessed_savesEntityWithMessageIdAndTransferId() {
        UUID messageId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        repo.markProcessed(messageId, transferId);

        ArgumentCaptor<ProcessedMessageJpaEntity> cap =
                ArgumentCaptor.forClass(ProcessedMessageJpaEntity.class);
        verify(jpa).save(cap.capture());

        ProcessedMessageJpaEntity saved = cap.getValue();
        assertThat(saved.getMessageId()).isEqualTo(messageId);
        assertThat(saved.getTransferId()).isEqualTo(transferId);
    }
}
