package com.fasttrans.account.domain.interfaces;

import com.fasttrans.account.domain.entities.Account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Domain contract for account persistence. Implemented in infrastructure. */
public interface AccountRepository {

    /** Finds an account by public ref — no lock (used to resolve ref → UUID and gRPC reads). */
    Optional<Account> findByAccountRef(String accountRef);

    /** Lists all accounts of a user — used for gRPC ListAccounts. */
    List<Account> findByUserId(UUID userId);

    /**
     * SELECT FOR UPDATE — locks the account before adjusting the balance.
     * Called in sorted UUID order to avoid deadlock when locking 2 accounts.
     */
    Optional<Account> lockById(UUID id);

    /** Persists balance changes on an account. */
    void save(Account account);
}
