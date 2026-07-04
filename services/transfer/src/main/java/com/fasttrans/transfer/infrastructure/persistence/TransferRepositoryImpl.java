package com.fasttrans.transfer.infrastructure.persistence;

import com.fasttrans.transfer.domain.entities.Transfer;
import com.fasttrans.transfer.domain.exception.DuplicateIdempotencyException;
import com.fasttrans.transfer.domain.interfaces.TransferRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JPA-backed implementation of the TransferRepository domain contract. */
@Repository
public class TransferRepositoryImpl implements TransferRepository {

    private final SpringDataTransferRepository jpa;
    private final TransferMapper mapper;

    public TransferRepositoryImpl(SpringDataTransferRepository jpa, TransferMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public void save(Transfer transfer) {
        try {
            // saveAndFlush forces the INSERT to hit the DB now, so a unique-constraint
            // violation on (userId, idempotencyKey) surfaces synchronously here — inside this
            // try/catch — regardless of any ambient transaction. This keeps the domain-exception
            // translation reliable rather than depending on deferred flush timing.
            jpa.saveAndFlush(mapper.toJpa(transfer));
        } catch (DataIntegrityViolationException e) {
            // Unique conflict on (userId, idempotencyKey) — translate to a domain exception
            // so the application can re-read without depending on Spring DAO types.
            throw new DuplicateIdempotencyException(
                    "Duplicate transfer for idempotency key=" + transfer.getIdempotencyKey(), e);
        }
    }

    @Override
    public Optional<Transfer> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Transfer> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return jpa.findByUserIdAndIdempotencyKey(userId, idempotencyKey).map(mapper::toDomain);
    }

    @Override
    public List<Transfer> findByUserIdOrderByCreatedAtDesc(UUID userId) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Transfer> findByIdAndUserId(UUID id, UUID userId) {
        return jpa.findByIdAndUserId(id, userId).map(mapper::toDomain);
    }
}
