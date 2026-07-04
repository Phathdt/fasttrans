package com.fasttrans.account.infrastructure.persistence;

import com.fasttrans.account.domain.entities.Account;
import org.mapstruct.Mapper;

/** Maps between the Account domain entity and its JPA representation. */
@Mapper(componentModel = "spring")
public interface AccountMapper {

    Account toDomain(AccountJpaEntity entity);

    AccountJpaEntity toJpa(Account account);
}
