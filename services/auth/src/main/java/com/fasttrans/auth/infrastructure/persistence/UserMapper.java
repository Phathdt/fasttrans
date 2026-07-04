package com.fasttrans.auth.infrastructure.persistence;

import com.fasttrans.auth.domain.entities.User;
import org.mapstruct.Mapper;

/** Maps the JPA user representation to the domain User (read-only direction). */
@Mapper(componentModel = "spring")
public interface UserMapper {

    User toDomain(UserJpaEntity entity);
}
