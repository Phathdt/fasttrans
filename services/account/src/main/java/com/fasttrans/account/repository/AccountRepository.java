package com.fasttrans.account.repository;

import com.fasttrans.account.entity.AccountEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    /** Finds an account by public ref — used for gRPC validate + event processing. */
    Optional<AccountEntity> findByAccountRef(String accountRef);

    /** Lists all accounts of a user — used for gRPC ListAccounts. */
    List<AccountEntity> findByUserId(UUID userId);

    /**
     * SELECT FOR UPDATE — lock the account before adjusting the balance.
     * Called in sorted UUID order to avoid deadlock when locking 2 accounts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a WHERE a.id = :id")
    Optional<AccountEntity> lockById(@Param("id") UUID id);
}
