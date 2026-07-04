package com.fasttrans.transfer.infrastructure.persistence;

import com.fasttrans.transfer.domain.entities.Transfer;
import com.fasttrans.transfer.domain.entities.TransferStatus;
import com.fasttrans.transfer.domain.exception.DuplicateIdempotencyException;
import com.fasttrans.transfer.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TransferRepositoryImpl.class, TransferMapperImpl.class})
class TransferRepositoryImplIT extends AbstractPostgresIT {

    @Autowired
    private TransferRepositoryImpl repository;

    @Test
    void save_and_findById_round_trips_correctly() {
        Transfer t = makeTransfer(UUID.randomUUID(), "idem-1");

        repository.save(t);
        Optional<Transfer> found = repository.findById(t.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getFromAccountRef()).isEqualTo("ACC001");
        assertThat(found.get().getStatus()).isEqualTo(TransferStatus.PENDING);
    }

    @Test
    void save_duplicate_idempotency_key_throws_DuplicateIdempotencyException() {
        UUID userId = UUID.randomUUID();
        String idemKey = "dup-key";

        repository.save(makeTransfer(userId, idemKey));

        Transfer duplicate = makeTransfer(userId, idemKey); // same userId + key, different id
        assertThatThrownBy(() -> repository.save(duplicate))
                .isInstanceOf(DuplicateIdempotencyException.class)
                .hasMessageContaining("dup-key");
    }

    @Test
    void findByUserIdAndIdempotencyKey_returns_existing() {
        UUID userId = UUID.randomUUID();
        Transfer t = makeTransfer(userId, "find-key");
        repository.save(t);

        Optional<Transfer> found = repository.findByUserIdAndIdempotencyKey(userId, "find-key");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(t.getId());
    }

    @Test
    void findByUserIdAndIdempotencyKey_returns_empty_for_unknown() {
        assertThat(repository.findByUserIdAndIdempotencyKey(UUID.randomUUID(), "no-key"))
                .isEmpty();
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_returns_transfers_newest_first() {
        UUID userId = UUID.randomUUID();
        Transfer t1 = makeTransfer(userId, "k-1");
        Transfer t2 = makeTransfer(userId, "k-2");
        repository.save(t1);
        repository.save(t2);

        List<Transfer> results = repository.findByUserIdOrderByCreatedAtDesc(userId);

        assertThat(results).hasSize(2);
    }

    @Test
    void findByIdAndUserId_returns_empty_for_wrong_user() {
        Transfer t = makeTransfer(UUID.randomUUID(), "key-x");
        repository.save(t);

        Optional<Transfer> result = repository.findByIdAndUserId(t.getId(), UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    private Transfer makeTransfer(UUID userId, String idemKey) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Transfer(UUID.randomUUID(), userId, idemKey,
                "ACC001", "ACC002", 100_000L, "VND",
                TransferStatus.PENDING, null, now, now);
    }
}
