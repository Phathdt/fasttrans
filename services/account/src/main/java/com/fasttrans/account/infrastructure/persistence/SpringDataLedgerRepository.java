package com.fasttrans.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataLedgerRepository extends JpaRepository<LedgerEntryJpaEntity, UUID> {
    // No custom query needed; save() is enough for the current use case.
}
