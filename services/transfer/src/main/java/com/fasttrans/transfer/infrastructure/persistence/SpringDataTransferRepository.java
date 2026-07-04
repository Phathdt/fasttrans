package com.fasttrans.transfer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataTransferRepository extends JpaRepository<TransferJpaEntity, UUID> {

    Optional<TransferJpaEntity> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    List<TransferJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<TransferJpaEntity> findByIdAndUserId(UUID id, UUID userId);
}
