package com.fasttrans.account.infrastructure.persistence;

import com.fasttrans.account.domain.entities.LedgerEntry;
import com.fasttrans.account.domain.interfaces.LedgerRepository;
import org.springframework.stereotype.Repository;

/** JPA-backed implementation of the LedgerRepository domain contract. */
@Repository
public class LedgerRepositoryImpl implements LedgerRepository {

    private final SpringDataLedgerRepository jpa;

    public LedgerRepositoryImpl(SpringDataLedgerRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(LedgerEntry entry) {
        LedgerEntryJpaEntity e = new LedgerEntryJpaEntity();
        e.setId(entry.getId());
        e.setAccountId(entry.getAccountId());
        e.setTransferId(entry.getTransferId());
        e.setDirection(entry.getDirection());
        e.setAmount(entry.getAmount());
        e.setBalanceAfter(entry.getBalanceAfter());
        jpa.save(e);
    }
}
