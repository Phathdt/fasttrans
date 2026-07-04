package com.fasttrans.account.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID> {

    /** Finds an account by public ref — used for gRPC validate + event processing. */
    Optional<AccountJpaEntity> findByAccountRef(String accountRef);

    /** Lists all accounts of a user — used for gRPC ListAccounts. */
    List<AccountJpaEntity> findByUserId(UUID userId);

    /**
     * SELECT FOR UPDATE — lock the account before adjusting the balance.
     * Called in sorted UUID order to avoid deadlock when locking 2 accounts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.id = :id")
    Optional<AccountJpaEntity> lockById(@Param("id") UUID id);
}
