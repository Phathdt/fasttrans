package com.fasttrans.transfer.repository;

import com.fasttrans.transfer.entity.TransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<TransferEntity, UUID> {

    Optional<TransferEntity> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    List<TransferEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<TransferEntity> findByIdAndUserId(UUID id, UUID userId);
}
