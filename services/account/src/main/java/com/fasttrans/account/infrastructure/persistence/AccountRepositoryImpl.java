package com.fasttrans.account.infrastructure.persistence;

import com.fasttrans.account.domain.entities.Account;
import com.fasttrans.account.domain.interfaces.AccountRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JPA-backed implementation of the AccountRepository domain contract. */
@Repository
public class AccountRepositoryImpl implements AccountRepository {

    private final SpringDataAccountRepository jpa;
    private final AccountMapper mapper;

    public AccountRepositoryImpl(SpringDataAccountRepository jpa, AccountMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<Account> findByAccountRef(String accountRef) {
        return jpa.findByAccountRef(accountRef).map(mapper::toDomain);
    }

    @Override
    public List<Account> findByUserId(UUID userId) {
        return jpa.findByUserId(userId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Account> lockById(UUID id) {
        // SELECT FOR UPDATE — must stay in the caller's transaction to hold the lock.
        return jpa.lockById(id).map(mapper::toDomain);
    }

    @Override
    public void save(Account account) {
        // merge()s into the row already locked by lockById in the same transaction;
        // the pessimistic lock only holds when save runs inside that locking transaction.
        jpa.save(mapper.toJpa(account));
    }
}
