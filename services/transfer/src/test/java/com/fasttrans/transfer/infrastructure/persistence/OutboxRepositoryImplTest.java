package com.fasttrans.transfer.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasttrans.transfer.application.dto.TransferRequestedEvent;
import com.fasttrans.transfer.domain.entities.Transfer;
import com.fasttrans.transfer.domain.entities.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxRepositoryImplTest {

    @Mock
    private SpringDataOutboxRepository jpa;

    private OutboxRepositoryImpl repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        repository = new OutboxRepositoryImpl(jpa, objectMapper);
    }

    @Test
    void enqueueTransferRequested_saves_outbox_entity_with_correct_fields() throws Exception {
        Transfer transfer = makeTransfer();

        repository.enqueueTransferRequested(transfer);

        ArgumentCaptor<OutboxJpaEntity> captor = ArgumentCaptor.forClass(OutboxJpaEntity.class);
        verify(jpa).save(captor.capture());
        OutboxJpaEntity saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo("transfer.requested");
        assertThat(saved.getMsgKey()).isEqualTo(transfer.getFromAccountRef());
        assertThat(saved.getPayload()).isNotBlank();

        // Verify payload is valid JSON with expected fields (contract from transfer-events.md)
        TransferRequestedEvent event = objectMapper.readValue(saved.getPayload(), TransferRequestedEvent.class);
        assertThat(event.transferId()).isEqualTo(transfer.getId().toString());
        assertThat(event.fromAccountRef()).isEqualTo(transfer.getFromAccountRef());
        assertThat(event.toAccountRef()).isEqualTo(transfer.getToAccountRef());
        assertThat(event.amount()).isEqualTo(transfer.getAmount());
        assertThat(event.currency()).isEqualTo(transfer.getCurrency());
        assertThat(event.messageId()).isNotBlank();
        assertThat(event.requestedAt()).isNotBlank();
    }

    private Transfer makeTransfer() {
        OffsetDateTime now = OffsetDateTime.now();
        return new Transfer(
                UUID.randomUUID(), UUID.randomUUID(), "idem-1",
                "ACC001", "ACC002", 100_000L, "VND",
                TransferStatus.PENDING, null, now, now);
    }
}
