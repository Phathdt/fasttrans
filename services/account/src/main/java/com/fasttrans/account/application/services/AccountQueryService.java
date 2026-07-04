package com.fasttrans.account.application.services;

import com.fasttrans.account.domain.entities.Account;
import com.fasttrans.account.domain.interfaces.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only queries backing the gRPC server (ValidateOwnership + ListAccounts).
 * No write transaction; uses default JPA reads via the repository.
 */
@Service
public class AccountQueryService {

    private final AccountRepository accountRepository;

    public AccountQueryService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** True when accountRef belongs to userId. Non-existent ref → false. */
    public boolean validateOwnership(String accountRef, UUID userId) {
        return accountRepository.findByAccountRef(accountRef)
                .map(account -> account.isOwnedBy(userId))
                .orElse(false);
    }

    /** All accounts of a user. Returns accountRef (public), not the internal UUID. */
    public List<Account> listAccounts(UUID userId) {
        return accountRepository.findByUserId(userId);
    }

    /** Lookup account by public ref for REST lookup endpoint. Empty when not found. */
    public Optional<Account> lookup(String accountRef) {
        return accountRepository.findByAccountRef(accountRef);
    }
}
