package com.fasttrans.account.repository;

import com.fasttrans.account.entity.OutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    /**
     * Fetches a PENDING batch with FOR UPDATE SKIP LOCKED — safe when running multiple instances.
     * ORDER BY created_at ensures FIFO ordering.
     */
    @Query(value = """
            SELECT * FROM outbox
            WHERE status = 'PENDING'
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT 100
            """, nativeQuery = true)
    List<OutboxEntity> lockPendingBatch();
}
