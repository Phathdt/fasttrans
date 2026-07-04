# Account Service: Clean Architecture Refactor (Phase 4)

**Date**: 2026-07-04  
**Severity**: Medium  
**Component**: Account service, domain layer restructure  
**Status**: Resolved  

## What Happened

Refactored `account` service from package-by-layer (config/controller/dto/entity/grpc/kafka/repository/service) to Clean Architecture / DDD 3-layer:

- **domain/** — Account aggregate, LedgerEntry, TransferResult; repository/client interfaces; InsufficientFundsException; all framework-free POJOs.
- **application/** — ProcessTransferService (@Transactional boundary), AccountQueryService (gRPC reads), use-case DTOs.
- **infrastructure/** — JpaEntity classes, SpringData repositories + RepositoryImpl adapters, AccountMapper (MapStruct), AccountGrpcService, OutboxRelay, TransferRequestedConsumer, KafkaConfig.

Tooling: MapStruct 1.6.3 (annotationProcessorPaths alongside protobuf), ArchUnit 1.3.0, spring-boot-starter-test. No dead code—removed AccountNotFoundException (never thrown; not-found handled via outbox TransferResult).

## The Brutal Truth

The highest-risk moment: **AccountRepositoryImpl.lockById** does `SELECT FOR UPDATE` then maps to a **detached** domain Account; `save()` rebuilds a JPA entity and calls `jpa.save()`. This pattern felt backwards at first—lock the row, then lose the connection. Spent 2 hours second-guessing whether merge() would actually update or insert. Code review + manual trace through Spring Data + Hibernate docs confirmed: **merge() goes to the locked row in the same @Transactional, @PreUpdate hook fires, updated_at preserved.** The invariant holds but the pattern is fragile. One wrong move (new Account() instead of merge) breaks atomicity silently. Added clarifying comments in the code.

The frustrating part: we nearly went hexagonal (ports/in, marker annotations, separate adapter layer), but that felt like cargo-culting Buckpal into a simple service. Pivoted to simpler DDD 3-layer. Right call, but cost an extra review cycle.

## Technical Details

- **Invariants verified**: I1 (ledger+balance+processed+outbox atomic), I2 (sorted-UUID lock order), I4 (inbox dedup), I6 (outbox relay blocks on broker ack). All hold post-refactor.
- **Behavior preserved**: API contracts unchanged, DB schema unchanged, Kafka topics unchanged, gRPC proto unchanged.
- **Build verification**: `mvn clean package` → BUILD SUCCESS. ArchUnit 3/3 passing (layer isolation rules). E2E smoke: 19/19 passing. Manual check: ledger-sum = account.balance across all test transfers.
- **Commit**: `9d07f55` on branch `refactor/account-clean-architecture`, conventional commit format, no AI references.

## What We Tried

1. Hexagonal with Buckpal pattern (ports/in UseCases, marker annotations). Too much plumbing for a 3-service system.
2. Reverted to simpler DDD: interfaces in domain/interfaces (owned by domain), no port/in layer. Spring stereotypes instead of custom markers. MapStruct for O/RM mapping.
3. Risk assessment on lockById: manual trace + peer review + ArchUnit integration tests to ensure repository layer doesn't leak entities.

## Root Cause Analysis

The earlier design (package-by-layer) mixed domain logic with Spring annotations (e.g., @Service on ProcessTransfer, repository interfaces tangled with JPA). No clear boundary between what's testable in isolation and what requires Spring. Refactor isolated domain logic (Account aggregate, interfaces) from infrastructure (Hibernate, Spring Data, messaging). Payoff: Account + LedgerEntry are now tested without Spring. Risk: mapping layer adds a layer of indirection that can hide bugs if not carefully validated.

## Lessons Learned

1. **Simpler is better than pattern-perfect.** Hexagonal + marker annotations felt "architecturally correct" but was premature for 3 services. DDD 3-layer got us separation of concerns without boilerplate.
2. **Detached entity patterns are fragile.** lockById → detached → merge() works but requires explicit understanding of Hibernate's session management. Future: consider LoadedDomainObject pattern or result types to make the flow explicit.
3. **ArchUnit pays off.** Caught a few package-crossing references that code review almost missed. Worth running in CI.
4. **Preserve invariants first, architecture second.** Ledger atomicity, lock order, dedup—those constraints drove the structure, not the other way around. Verified them before declaring "done."

## Next Steps

1. **Phase 2: Transfer service.** Same 3-layer refactor. Apply lessons: skip ports/adapters, keep MapStruct, watch the lockById pattern in SaveTransfer.
2. **Phase 3: Auth service.** Simpler scope (sessions, login), but same structure for consistency.
3. **CI integration:** Add ArchUnit to GitHub Actions (phase-gate before merge).
4. **Document the mapping pattern:** Write a brief note on why lockById detaches entities and why merge() is safe here. Next dev won't spend 2 hours on this.

---

**Unresolved questions:**
- Auth + transfer still on package-by-layer pending Phases 2/3. Should we delay their refactor until all 3 are aligned, or proceed independently?
