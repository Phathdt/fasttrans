---
phase: 4
title: "Account Service"
status: completed
priority: P1
dependencies: [3]
---

# Phase 4: Account Service (Most Critical)

## Overview

Refactor `account` — phức tạp nhất: ledger + lock + outbox trong 1 transaction (I1), lock order (I2), inbox dedup (I4), outbox relay (I6), gRPC server. Làm cuối khi pattern đã chín. **Cẩn trọng tối đa với transaction boundary.**

## Requirements

- Functional: xử lý `transfer.requested` (debit/credit/ledger/result), gRPC `ValidateOwnership`+`ListAccounts` không đổi.
- Non-functional: domain `Account`/`LedgerEntry` thuần; toàn bộ invariant I1/I2/I4/I6 nguyên vẹn.

## Architecture

```
com.fasttrans.account
├── domain/
│   ├── entities/     Account (debit/credit → raise InsufficientFundsException),
│   │                 LedgerEntry, TransferResult value
│   ├── interfaces/   AccountRepository (load, lockById=SELECT FOR UPDATE, save),
│   │                 LedgerRepository, OutboxRepository, InboxRepository
│   └── exception/    InsufficientFundsException, AccountNotFoundException
├── application/
│   ├── dto/          TransferRequestedEvent, TransferResultEvent (event payload)
│   └── services/     ProcessTransferService @Service — @Transactional (I1);
│                     AccountQueryService (gRPC read: validateOwnership, listAccounts)
└── infrastructure/
    ├── grpc/         AccountGrpcService @GrpcService (inbound) → AccountQueryService
    ├── messaging/    TransferRequestedConsumer (inbound) → ProcessTransferService,
    │                 OutboxRelay (outbound) + event serializer
    ├── persistence/  AccountJpaEntity, LedgerEntryJpaEntity, OutboxJpaEntity,
    │                 ProcessedMessageJpaEntity, SpringData repos (lockById, lockPendingBatch),
    │                 *RepositoryImpl, AccountMapper (MapStruct)
    └── config/       KafkaConfig
```

**Điểm mấu chốt — I1 transaction boundary:**
- `ProcessTransferService.process` `@Transactional` bọc TOÀN BỘ: inbox check → resolve accounts → lock (I2) → balance check → ledger write → balance update → outbox → processed_messages. **Tất cả repository call trong 1 transaction Spring** — interface chỉ là contract, impl `@Repository` dùng cùng `EntityManager`/tx context (Spring proxy). Không được tách thành nhiều @Transactional nhỏ.
- **Lock order I2**: `AccountRepository.lockById(uuid)` (SELECT FOR UPDATE). Thứ tự sort UUID phải giữ TRONG application service (đây là domain rule, không phải infra). Service gọi `lockById` theo sorted order.
- **Domain logic**: `Account.debit(amount)` ném `InsufficientFundsException` (domain); `adjustBalance` → `debit/credit`. Service bắt exception domain → outbox FAILED/INSUFFICIENT. `AccountNotFoundException` khi resolve ref rỗng.
- **Outbox serialization**: domain/application `TransferResult` event → `OutboxRepository.enqueue`; Jackson ở infrastructure.
- **gRPC server** (infrastructure/grpc): `AccountGrpcService` gọi `AccountQueryService` (read-only, không tx write). Giữ behavior: ref không tồn tại → owned=false, exception → owned=false / empty list.

## Related Code Files

- Create: `domain/entities/Account.java`, `LedgerEntry.java`, `TransferResult.java`
- Create: `domain/exception/InsufficientFundsException.java`, `AccountNotFoundException.java`
- Create: `domain/interfaces/AccountRepository.java`, `LedgerRepository.java`, `OutboxRepository.java`, `InboxRepository.java`
- Create: `application/services/ProcessTransferService.java`, `AccountQueryService.java`
- Move: `dto/*` → `application/dto/`
- Create: `infrastructure/grpc/AccountGrpcService.java`, `infrastructure/messaging/TransferRequestedConsumer.java`, `OutboxRelay.java` + serializer
- Create: `infrastructure/persistence/*JpaEntity`, `SpringData*Repository`, `*RepositoryImpl`, `AccountMapper`
- Move: `config/KafkaConfig` → `infrastructure/config/`
- Delete: cũ trong `grpc/`, `kafka/`, `service/`, `entity/`, `repository/`, `dto/`
- Keep: proto (`account.proto`, generated packages `com.fasttrans.account.grpc.*`), Flyway

## Implementation Steps

1. `domain/entities/Account` (debit/credit + `InsufficientFundsException`), `LedgerEntry`, `TransferResult`; `domain/interfaces` + `domain/exception`.
2. Persistence: rename entities → `*JpaEntity`; SpringData repos giữ `lockById` (`@Lock(PESSIMISTIC_WRITE)` SELECT FOR UPDATE) + `lockPendingBatch`; `*RepositoryImpl` `@Repository` impl domain interface; `AccountMapper` (MapStruct `@Mapper(componentModel="spring")`).
3. `ProcessTransferService` `@Transactional`: **copy nguyên logic** `AccountService.process` — inbox dedup → resolve → lock sorted (I2) → balance/debit-credit → ledger → outbox → processed. Chỉ thay repo call bằng domain-interface call. KHÔNG đổi thứ tự.
4. messaging: `OutboxRelay` (outbound) + serializer; `TransferRequestedConsumer` (inbound) parse→event DTO.
5. gRPC: `AccountGrpcService` (infrastructure/grpc) → `AccountQueryService`; giữ error-swallow behavior.
6. Build + smoke test: COMPLETED + INSUFFICIENT_FUNDS + ACCOUNT_NOT_FOUND paths; verify `ledger sum == balance` (I1).

## Success Criteria

- [x] `account` build xanh (`mvn clean package`, BUILD SUCCESS).
- [x] Smoke test: transfer COMPLETED; insufficient → FAILED/INSUFFICIENT_FUNDS; ACCOUNT_NOT_FOUND; ledger sum khớp balance (I1) — 19/19 pass, ledger-sum verified qua psql.
- [x] gRPC ValidateOwnership/ListAccounts trả kết quả đúng (smoke section 3 + 4).
- [x] Duplicate messageId bị skip (I4); outbox relay at-least-once (I6) — code review xác nhận.
- [x] Domain `Account`/`LedgerEntry` + `domain/interfaces` không import Spring/JPA/Kafka (ArchUnit `CleanArchitectureTest` 3/3 pass).

## Execution Notes (Session 2026-07-04)

- Gộp Phase 1 tooling cho account module (MapStruct 1.6.3 + `annotationProcessorPaths`, ArchUnit 1.3.0, `spring-boot-starter-test`) vào `services/account/pom.xml` — trước đó chưa áp.
- Cấu trúc mới: `domain/{entities,interfaces,exception}`, `application/{dto,services}`, `infrastructure/{persistence,grpc,messaging,config}`. Xóa toàn bộ package cũ (config/controller/dto/entity/grpc/kafka/repository/service).
- Code review (code-reviewer subagent): DONE_WITH_CONCERNS. KEY CONCERN `lockById → detached domain → save` verified SAFE (merge vào managed locked row trong cùng `@Transactional`; id gán sẵn → UPDATE không INSERT; `@PreUpdate` giữ nguyên).
- Xử lý finding: xóa `AccountNotFoundException` (dead code, YAGNI); thêm comment merge-dependency ở `AccountRepositoryImpl.save`.
- Test coverage: chỉ structural (ArchUnit) + smoke — không phát sinh unit test domain (đúng scope Phase 5 đã chốt).

## Risk Assessment

- **Cao**: phá @Transactional boundary → mất tính nguyên tử ledger+outbox. Mitigation: 1 @Transactional duy nhất ở `ProcessTransferService`; verify bằng ledger-sum check + kill-during-tx test nếu có thời gian.
- **Cao**: sai lock order → deadlock dưới tải. Mitigation: giữ sort UUID nguyên trong service; không refactor thuật toán.
- SELECT FOR UPDATE qua interface bị JPA đọc cache thay vì lock → giữ `@Lock(PESSIMISTIC_WRITE)` query hiện có trong SpringData repo, `RepositoryImpl` gọi thẳng.
