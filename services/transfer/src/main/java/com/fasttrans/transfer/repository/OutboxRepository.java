package com.fasttrans.transfer.repository;

import com.fasttrans.transfer.entity.OutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    // Relay poll: fetch a PENDING batch. FOR UPDATE SKIP LOCKED is inlined in the SQL (a native query
    // does not honor JPA's @Lock), locking the rows so multi-instance relay is safe.
    @Query(value = "SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY created_at "
            + "FOR UPDATE SKIP LOCKED LIMIT 100", nativeQuery = true)
    List<OutboxEntity> lockPendingBatch();
}
