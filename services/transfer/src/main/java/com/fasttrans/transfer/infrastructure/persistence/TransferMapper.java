package com.fasttrans.transfer.infrastructure.persistence;

import com.fasttrans.transfer.domain.entities.Transfer;
import org.mapstruct.Mapper;

/**
 * Maps between the Transfer domain entity and its JPA representation.
 * TransferStatus enum ↔ String status is handled by MapStruct's default enum-name mapping.
 */
@Mapper(componentModel = "spring")
public interface TransferMapper {

    Transfer toDomain(TransferJpaEntity entity);

    TransferJpaEntity toJpa(Transfer transfer);
}
