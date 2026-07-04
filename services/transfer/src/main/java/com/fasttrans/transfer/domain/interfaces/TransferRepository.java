package com.fasttrans.transfer.domain.interfaces;

import com.fasttrans.transfer.domain.entities.Transfer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Domain contract for transfer persistence. Implemented in infrastructure. */
public interface TransferRepository {

    /**
     * Persists a transfer. Implementations MUST translate a unique-constraint violation
     * on (userId, idempotencyKey) into DuplicateIdempotencyException.
     */
    void save(Transfer transfer);

    Optional<Transfer> findById(UUID id);

    Optional<Transfer> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    List<Transfer> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Transfer> findByIdAndUserId(UUID id, UUID userId);
}
