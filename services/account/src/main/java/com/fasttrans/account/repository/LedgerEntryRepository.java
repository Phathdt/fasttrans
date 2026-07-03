package com.fasttrans.account.repository;

import com.fasttrans.account.entity.LedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {
    // No custom query needed; save() is enough for the current use case.
}
