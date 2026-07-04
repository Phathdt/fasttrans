package com.fasttrans.transfer.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboxRepositoryImplTest {

    @Mock
    private SpringDataProcessedMessageRepository jpa;

    private InboxRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new InboxRepositoryImpl(jpa);
    }

    @Test
    void isProcessed_returns_true_when_message_exists() {
        UUID messageId = UUID.randomUUID();
        when(jpa.existsById(messageId)).thenReturn(true);

        assertThat(repository.isProcessed(messageId)).isTrue();
    }

    @Test
    void isProcessed_returns_false_when_message_not_exists() {
        UUID messageId = UUID.randomUUID();
        when(jpa.existsById(messageId)).thenReturn(false);

        assertThat(repository.isProcessed(messageId)).isFalse();
    }

    @Test
    void markProcessed_saves_processed_message_entity() {
        UUID messageId = UUID.randomUUID();

        repository.markProcessed(messageId);

        verify(jpa).save(argThat(entity ->
                entity instanceof ProcessedMessageJpaEntity &&
                ((ProcessedMessageJpaEntity) entity).getMessageId().equals(messageId)));
    }
}
