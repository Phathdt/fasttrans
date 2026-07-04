---
title: "Clean Architecture + DDD Refactor - 3 Services"
description: ""
status: pending
priority: P2
branch: "main"
tags: []
blockedBy: []
blocks: []
created: "2026-07-04T00:20:58.267Z"
createdBy: "ck:plan"
source: skill
---

# Clean Architecture + DDD Refactor - 3 Services

## Overview

Refactor 3 Spring Boot microservices (`auth`, `transfer`, `account`) từ **package-by-layer** sang **Clean Architecture / DDD 3-layer** (`domain` / `application` / `infrastructure`). Thuần cấu trúc — **KHÔNG đổi behavior, KHÔNG đổi API/DB/proto**.

Mô hình target (DDD layering, tham chiếu ví dụ Go clean-arch):
```
com.fasttrans.<svc>
├── domain/
│   ├── entities/     POJO thuần + business logic (zero Spring/JPA/Jackson/gRPC/Kafka)
│   ├── interfaces/   Repository + external-service interfaces — DOMAIN sở hữu contract
│   └── exception/    Domain exceptions (InsufficientFunds, DuplicateIdempotency, ...)
├── application/
│   ├── dto/          Request/Response DTO, Command, event payload DTO
│   └── services/     Application service (@Service) — orchestration + @Transactional boundary
└── infrastructure/
    ├── web/          REST controller + GlobalExceptionHandler (inbound)
    ├── messaging/    Kafka consumer (inbound) + producer/OutboxRelay (outbound) + serializer
    ├── grpc/         gRPC server (inbound) và/hoặc gRPC client (outbound)
    ├── persistence/  *JpaEntity, Spring Data repo, repository impl (impl domain interface), mapper
    └── config/       Spring wiring (KafkaConfig, RedisConfig, ...)
```

**Nguyên tắc dependency**: `infrastructure → application → domain`. Domain không phụ thuộc gì; application chỉ phụ thuộc domain; infrastructure impl các interface trong `domain/interfaces`.

**Khác biệt cốt lõi so với hexagonal buckpal (bản plan trước):**
- Repository & external-client interface (port) nằm ở **`domain/interfaces`** (domain sở hữu), không phải `application/port/out`.
- **Không** có `port/in` / `*UseCase` interface — application service là **class cụ thể**, infrastructure inbound gọi thẳng.
- **Không** tách `adapter/in` vs `adapter/out`; `infrastructure` gom theo công nghệ (web/messaging/grpc/persistence), mỗi nhóm có thể chứa cả inbound lẫn outbound.
- **Bỏ** marker annotations — dùng Spring stereotype chuẩn (`@Service`, `@RestController`, `@Repository`, `@Component`).

Tooling: **MapStruct** (mapper domain↔JPA, `componentModel="spring"`) + **ArchUnit** (enforce dependency rule 3-layer). Giao hàng: **1 PR gộp**, thứ tự thực thi `auth → transfer → account` (đơn giản → phức tạp).

**Nguồn:** [research report](../reports/clean-architecture-refactor-260704-0715-services-research-report.md).

## Invariants — TUYỆT ĐỐI bảo toàn

| # | Invariant | Vị trí gốc |
|---|-----------|-----------|
| I1 | `account.process`: ledger + balance + processed_messages + outbox trong **1 `@Transactional`** | `AccountService.process` |
| I2 | Lock 2 account theo **thứ tự UUID sort** (deadlock avoidance) | `AccountService` |
| I3 | Idempotency `(userId, idempotencyKey)` unique + xử lý race `DataIntegrityViolationException` | `TransferService.create` |
| I4 | Inbox dedup qua `processed_messages` (cả 2 service) | `process`/`applyResult` |
| I5 | gRPC deadline 5s; `UNAVAILABLE/DEADLINE_EXCEEDED` → 503 | `AccountGrpcClient` + `TransferService` |
| I6 | Outbox relay: block chờ broker ack trước khi mark SENT (at-least-once) | `OutboxRelay` |
| I7 | JWT + Redis session revocable; verify = signature OK **AND** key còn trong Redis | `TokenService` |
| I8 | HTTP error mapping RFC7807 (403/404/503/400) không đổi | `GlobalExceptionHandler` |
| I9 | OpenAPI spec không đổi (springdoc `@Operation`/`@Tag`/`@Parameter` ở lại web layer) | `TransferController` |
| I10 | Flyway migrations, tên bảng/cột, proto-generated packages (`com.fasttrans.account.grpc.*`) giữ nguyên | — |

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Foundation & Tooling](./phase-01-foundation-tooling.md) | Pending |
| 2 | [Auth Service](./phase-02-auth-service.md) | ✅ Done |
| 3 | [Transfer Service](./phase-03-transfer-service.md) | ✅ Done |
| 4 | [Account Service](./phase-04-account-service.md) | ✅ Done |
| 5 | [Enforcement & Docs](./phase-05-enforcement-docs.md) | Pending |

## Acceptance Criteria (toàn plan)

- [ ] Cả 3 service build xanh (`mvn -q package`) sau refactor.
- [ ] `bash scripts/e2e-smoke.sh` pass giống baseline (behavior không đổi).
- [ ] ArchUnit test pass ở cả 3 service: `domain` không phụ thuộc `application`/`infrastructure`/framework; `application` chỉ phụ thuộc `domain`.
- [ ] OpenAPI spec (`/v3/api-docs.yaml`) của auth+transfer khớp baseline (diff rỗng về path/schema).
- [ ] Ledger sum = balance sau smoke test (invariant I1 nguyên vẹn).
- [ ] Không thay đổi file Flyway migration, proto, docker-compose.

## Dependencies

- Phase 1 blocks 2,3,4 (tooling + ArchUnit dependency dùng chung).
- Phase 2,3,4 tuần tự (validate pattern trên auth trước).
- Phase 5 blocked by 2,3,4.
- Không cross-plan dependency (plan `260703-1537` đã `done`).

## Validation Log

### Session 1 (2026-07-04)

**Verification Results (Full tier, 5 phases)**
- Claims checked: 4 | Verified: 3 | Failed: 0 | Unverified: 0 (1 finding chỉnh sửa)
- ✅ `@Lock(PESSIMISTIC_WRITE) lockById` tồn tại (`account/repository/AccountRepository.java:26-28`) → I2 chính xác.
- ✅ ArchUnit chưa có trong pom → đúng, thêm ở Phase 1.
- ⚠️ **Finding (fixed)**: KHÔNG service nào có `spring-boot-starter-test`, không có test dir. → Phase 1 cập nhật: thêm test starter cho cả 3.

**Decisions confirmed**
1. **Race idempotency (Phase 3)**: infrastructure repo impl wrap unique-violation thành `DuplicateIdempotencyException` (domain exception); application service catch để re-read. Domain interface không lộ Spring DAO.
2. **Test scope (Phase 5)**: chỉ ArchUnit (dependency rule) + smoke test hiện có; KHÔNG phát sinh unit test domain/service mới (YAGNI cho demo).
3. **Commit strategy**: commit từng service riêng (verify build+smoke giữa các commit, dễ bisect), vẫn gộp 1 PR cuối.

### Session 2 (2026-07-04) — Architecture revision

Người dùng yêu cầu đổi từ **hexagonal buckpal** (adapter/port in-out + marker + MapStruct) sang **Clean Architecture / DDD 3-layer** (`domain`/`application`/`infrastructure`) theo ví dụ Go. Cập nhật toàn bộ plan + 5 phase:
- **Ports (interfaces) chuyển về `domain/interfaces`** — domain sở hữu contract (repository, external client). Bỏ `application/port/out`.
- **Bỏ `port/in` / `*UseCase`** — application service là class cụ thể (`@Service`), infrastructure gọi thẳng.
- **`infrastructure` gom theo công nghệ** (web/messaging/grpc/persistence/config) thay cho `adapter/in` + `adapter/out`.
- **Bỏ marker annotations** → Spring stereotype chuẩn.
- **Giữ MapStruct** (mapper domain↔JPA, `componentModel="spring"`) — cấu hình `annotationProcessorPaths` trong `maven-compiler-plugin`, chú ý thứ tự với protobuf plugin.
- ArchUnit giữ lại, rule đơn giản hóa theo 3-layer.
- Invariants I1–I10 **không đổi** (behavior giữ nguyên).

**Unresolved questions**
- Không có. Plan sẵn sàng implement.
