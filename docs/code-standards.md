# FastTrans Code Standards

## Overview

This document defines coding standards, architectural patterns, and conventions for the FastTrans project. All Java services follow Clean Architecture / DDD principles with enforced ArchUnit rules. Frontend follows modern React + TypeScript conventions.

## General Principles

- **YAGNI**: Do not build for future features not yet requested
- **KISS**: Keep code simple and obvious; avoid premature optimization
- **DRY**: Avoid duplication, but not at the cost of tight coupling
- **Explicit over implicit**: Clear intent beats clever code
- **Framework-free domain**: No Spring, JPA, or library annotations in business logic

## Java Services Architecture

### Package Structure (Clean Architecture 3-Layer)

All Java services follow the same structure:

```
com.fasttrans.{service}/
├─ domain/
│  ├─ entities/          Pure POJOs (no annotations, no getters/setters required)
│  ├─ interfaces/        Repository + service contracts (domain-owned)
│  └─ exception/         Domain-level exceptions
├─ application/
│  ├─ dto/               REST request/response, Kafka event payloads
│  └─ services/          @Service, @Transactional, orchestration logic
└─ infrastructure/
   ├─ web/               @RestController, @ExceptionHandler
   ├─ grpc/              gRPC server (account) / client (transfer)
   ├─ messaging/         Kafka consumer, OutboxRelayService
   ├─ persistence/       JPA @Entity, Spring Data Repository, *RepositoryImpl, MapStruct mapper
   ├─ config/            Spring @Configuration, Bean definitions
   ├─ security/          JWT, auth-specific (auth service only)
   └─ session/           Redis, session-specific (auth service only)
```

**Dependency Rule**: `infrastructure → application → domain`. Enforced by ArchUnit in each service's `CleanArchitectureTest`.

### Domain Layer (domain/)

**Purpose**: Pure business logic, framework-agnostic, testable without Spring.

**Entities** (domain/entities/):
- POJO classes with `private` fields, getter/setter methods (standard Java Bean)
- No Spring annotations (@Entity, @Data, @Getter, etc.)
- Constructor overloading (default + full)
- Example: `User(UUID id, String username, String passwordHash)`

**Interfaces** (domain/interfaces/):
- **Repository interfaces**: abstract persistence (e.g., `UserRepository`, `TransferRepository`)
- **Service contracts**: external dependencies (e.g., `TokenService`, `AccountServiceClient`)
- No Spring annotations; plain Java interfaces
- Example: `UserRepository { User findByUsername(String); void save(User); }`

**Exceptions** (domain/exception/):
- Extend `RuntimeException` (unchecked) for consistency with Spring @Transactional rollback
- Domain-specific (e.g., `InsufficientFundsException`, `AccountNotFound`)
- Include message + optional cause

### Application Layer (application/)

**Purpose**: Use cases and data transfer, thin orchestration boundary.

**DTOs** (application/dto/):
- REST request/response payloads (JSON serializable)
- Kafka event payloads (JSONB in DB)
- Example: `CreateTransferRequest { String fromAccountRef; String toAccountRef; long amount; }`
- No business logic; just properties + getters/setters

**Services** (application/services/):
- `@Service` + `@Transactional` on business methods
- Orchestrate domain logic (call domain.interfaces, handle exceptions, delegate persistence)
- Single responsibility (e.g., `TransferService`, not `TransferAndAccountService`)
- Example: `void createTransfer(String userId, CreateTransferRequest) throws TransferException`
- OutboxRelayService: `@Scheduled(fixedDelay=1000)`, no transaction (read-only poll + send)

### Infrastructure Layer (infrastructure/)

**Purpose**: Framework-specific implementations, API contracts, external integrations.

**Web** (infrastructure/web/):
- `@RestController` with `@GetMapping`, `@PostMapping`
- Accept `application.dto.*` types, return same
- `@GlobalExceptionHandler` maps domain exceptions to HTTP status (e.g., `InsufficientFundsException` → 400)
- No business logic; call application.services

**gRPC** (infrastructure/grpc/):
- **Server** (account service): `AccountServiceImpl` implements gRPC-generated service interface
- **Client** (transfer service): `AccountServiceGrpcClient` implements domain `AccountServiceClient` interface; wraps gRPC stub
- Both use protobuf-generated classes; no manual proto handling

**Messaging** (infrastructure/messaging/):
- Kafka consumers: deserialization + call application.services
- OutboxRelayScheduler: `@Scheduled` polls outbox, publishes via KafkaTemplate, updates status
- No transaction scope; coordinator handles consistency

**Persistence** (infrastructure/persistence/):
- `*JpaEntity` (e.g., `UserJpaEntity`): JPA `@Entity`, no domain logic
- `Spring*Repository`: Spring Data `@Repository` extending `JpaRepository` (e.g., `SpringDataUserRepository`)
- `*RepositoryImpl`: implements domain repository interface; uses Spring repo + mapper
- `*Mapper`: MapStruct `@Mapper(componentModel = "spring")` (e.g., `UserMapper`, converts JpaEntity ↔ domain Entity)

**Config** (infrastructure/config/):
- `@Configuration` classes for Spring beans, Kafka template, Redis template, gRPC channel
- Property-based (e.g., `@ConfigurationProperties`, `@Value`)
- No business logic

**Security** (infrastructure/security/, auth service only):
- `JwtTokenService`: implements domain `TokenService`; uses jjwt library
- No @Service annotation (created as @Bean in config)

**Session** (infrastructure/session/, auth service only):
- `RedisSessionStore`: implements domain `SessionStore`; uses RedisTemplate
- No @Service annotation (created as @Bean in config)

## Naming Conventions

### Java
- **Classes**: PascalCase (`UserRepository`, `AuthService`, `UserJpaEntity`)
- **Methods**: camelCase (`findByUsername`, `createTransfer`, `validateOwnership`)
- **Constants**: SCREAMING_SNAKE_CASE (`DEFAULT_TIMEOUT`, `KAFKA_TOPIC_TRANSFER_REQUESTED`)
- **Packages**: lowercase, dot-separated (`com.fasttrans.auth.domain.entities`)
- **Test classes**: `*Test` or `*Tests` suffix (e.g., `UserServiceTest`, `CleanArchitectureTest`)

### SQL
- **Tables**: snake_case (`users`, `transfer_requests`, `ledger_entries`)
- **Columns**: snake_case (`user_id`, `created_at`, `password_hash`)
- **Indexes**: `idx_{table}_{column}` or `idx_{table}_{logic}` (e.g., `idx_accounts_user`, `idx_outbox_pending`)
- **Constraints**: explicit names (e.g., `uq_transfers_user_idem`, `fk_ledger_account`)

### TypeScript / JavaScript (Frontend)
- **Files**: kebab-case (`account-list.tsx`, `api-client.ts`, `transfer-form.tsx`)
- **Components**: PascalCase (`AccountList`, `TransferForm`)
- **Constants**: SCREAMING_SNAKE_CASE (`TRANSFER_POLLING_INTERVAL_MS`)
- **Exports**: `export default`, single component per file

## Code Organization Rules

### Java Services

1. **No circular dependencies**: If A imports B, B cannot import A (enforced at layer boundary)
2. **One entity per file**: Don't put multiple domain entities in one file
3. **Test colocation**: Test in `src/test/java` with same package structure as `src/main/java`
4. **Repository implementation**: Always separate interface (domain) from impl (infrastructure)
5. **Mappers**: One mapper per entity type; use MapStruct `@Mapper`, not manual conversion
6. **No @Service in domain**: Domain entities and interfaces must not use Spring annotations
7. **No JPA in application**: Application.dto and application.services must not import JPA
8. **Constants**: Define in domain (e.g., `TransferStatus.PENDING`) or application, not scattered

### Frontend

1. **Component structure**: One component per file (PascalCase filename matches component name)
2. **API client**: Auto-generated in `src/api/generated/` by orval; never hand-edit
3. **Hooks**: Custom hooks in `src/hooks/`, named `use*` (e.g., `useTransfers`, `useAuth`)
4. **Utils**: Pure functions in `src/utils/`, no side effects
5. **Types**: Define in same file or dedicated `types.ts` if shared across multiple files
6. **Imports**: Absolute paths via `@/` alias (src root)

## Patterns & Best Practices

### Domain-Driven Design (DDD)

- **Entity**: Mutable object with identity (e.g., `User`, `Transfer`)
- **Value Object**: Immutable, no identity (e.g., `Money` as `{ amount: long; currency: string }`)
- **Aggregate**: Group of entities/value objects (e.g., `Transfer` aggregate contains Transfer + Outbox row)
- **Repository**: Contract for persisting aggregates (domain-owned interface)
- **Service**: Orchestrates domain logic (application.services in our case, not domain.services)

### Transactional Consistency

- **Transactional Outbox**: Write business change + event in same transaction; relay publishes later
- **Inbox Dedup**: Consumer stores messageId before processing; idempotent if message replayed
- **At-least-once**: Outbox crashes between send + status update = re-publish; consumer absorbs via inbox
- **Idempotency Key**: API dedup on `(user_id, idempotency_key)` unique constraint; replay returns original

### Error Handling

- **Domain exceptions**: Extend `RuntimeException`; thrown by domain logic (e.g., `InsufficientFundsException`)
- **Application catching**: `application.services` catch domain exceptions, translate to HTTP status
- **GlobalExceptionHandler**: Maps exception type to HTTP code (e.g., 400, 403, 503)
- **No null returns**: Use exceptions or `Optional<T>` (prefer exceptions in domain)

### Testing

- **ArchUnit**: Enforces 3-layer dependency rules (`CleanArchitectureTest` per service)
- **Unit tests** (`*Test`, Surefire): Test domain entities + application services without Spring (mock repositories via Mockito); controllers via `@WebMvcTest`
- **Integration tests** (`*IT`, Failsafe): Testcontainers-backed. Persistence via `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)`; messaging/gRPC via `@SpringBootTest`
- **E2E tests**: Docker Compose + bash script; smoke-test all API endpoints

#### Coverage & tooling

- **JaCoCo gate**: ≥90% LINE per service, enforced at `mvn verify` (merged unit + integration exec). Build fails below threshold.
- **Excluded from coverage**: generated gRPC stubs (`**/grpc/**`), MapStruct `*MapperImpl`, `*Application`, `**/config/**`, `**/application/dto/**`, `*JpaEntity`. Domain entities are NOT excluded.
- **Testcontainers images**: `postgres:16-alpine`, `redpandadata/redpanda:v24.2.7` (Redpanda, not plain Kafka), `redis:7-alpine`. Singleton container pattern per base class in `support/`.
- **Run**: `mvn clean verify` from a service dir (requires Docker running). Fast unit-only run without Docker: `mvn verify -DskipITs`.
- **Coverage report**: `target/site/jacoco-merged/index.html` per service.
- **Docker Engine 29+ note**: Failsafe pins `-Dapi.version=1.44` (docker-java system property) + `TESTCONTAINERS_RYUK_DISABLED=true`; the `${argLine}` prefix is required to keep the JaCoCo agent.

Current coverage: auth 97.3%, transfer 99.1%, account 95.4% LINE.

## Money & Numbers

- **Storage**: `bigint` (smallest unit; 1 VND = 1)
- **API transfer**: JSON `number` (JavaScript safe integers up to 2^53-1; ~8 quadrillion VND)
- **No floats**: Never use `double` or `float` for money
- **Precision**: All calculations integer-only (no rounding errors)

## Concurrency & Locking

- **Database-level**: `SELECT ... FOR UPDATE` on mutable entities before write
- **Deadlock prevention**: Lock in sorted order (e.g., by UUID ascending)
- **Spring @Transactional**: Ensures transaction boundary; rollback on exception
- **Outbox relay**: `FOR UPDATE SKIP LOCKED` allows multiple instances without duplicate publish

## Logging & Observability

- **Structured logging**: Use SLF4J with `{}` placeholders (e.g., `log.info("Transfer created: {}", transferId)`)
- **Log levels**: DEBUG (detail), INFO (important events), WARN (recoverable issues), ERROR (failures)
- **No PII in logs**: Never log passwords, tokens, or full account numbers
- **Correlation ID**: (Future) Pass `X-Request-Id` through request lifecycle for tracing

## Version Management

- **Java version**: 21 LTS (spring-boot 3.3.4 compatible)
- **Spring Boot**: 3.3.4
- **Maven**: 3.9+
- **Node.js**: 24.x (frontend)
- **pnpm**: 9.x (frontend)

## Code Review Checklist

- [ ] New/modified code follows package structure (domain/application/infrastructure)
- [ ] No framework annotations in domain.entities or domain.interfaces
- [ ] No JPA imports in application.* layers
- [ ] Exceptions thrown are domain-specific (not generic RuntimeException)
- [ ] Mappers used for entity ↔ DTO conversion (no manual assignment loops)
- [ ] Database changes require migration file (Flyway V*__*.sql)
- [ ] Tests added for new domain logic or API endpoints (unit `*Test` and/or integration `*IT`)
- [ ] `mvn clean verify` passes with JaCoCo ≥90% LINE (Docker required for `*IT`)
- [ ] ArchUnit tests pass (if service modified): `mvn test -Dtest=CleanArchitectureTest`
- [ ] No hardcoded secrets or sensitive values
- [ ] Naming follows conventions (camelCase, snake_case, PascalCase per context)

## Documentation Requirements

- **README.md**: Updated if build/run commands change
- **CLAUDE.md**: Updated if package structure or conventions change
- **docs/services/*.md**: Updated if API endpoints or event contracts change
- **docs/db/schema.md**: Updated if DB migrations applied
- **Inline comments**: Explain *why*, not *what* (code is self-documenting via naming)

## Anti-Patterns

❌ **Do NOT**:
- Use `Double` or `Float` for money
- Put business logic in REST controller or JPA entity
- Import Spring in domain.entities or domain.interfaces
- Catch `Exception` (catch specific exceptions)
- Use `Optional.get()` without `isPresent()` check (use `.orElseThrow()` or `.ifPresent()`)
- Hard-code configuration values (use `@Value`, `@ConfigurationProperties`)
- Skip @Transactional boundary (always mark orchestration methods)
- Reuse JpaEntity as DTO (always map to application.dto)

## Updates & Maintenance

This document is the source of truth. When making code changes that affect standards:
1. Update this file first
2. Apply changes to codebase
3. Run tests and ArchUnit verification
4. Update `docs/README.md` or service-specific docs if contracts changed
5. Commit with clear message referencing the standard updated

---

**Last Updated**: 2026-07-04  
**Versions**: Java 21, Spring Boot 3.3.4, Clean Architecture enforced by ArchUnit
