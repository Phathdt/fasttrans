# Auth & Transfer Services: Clean Architecture Refactor (Phases 2–3)

**Date**: 2026-07-04  
**Severity**: Medium  
**Component**: Auth & Transfer services, domain layer restructure  
**Status**: Resolved (M1 pre-existing, flagged)  

## What Happened

Completed Phases 2–3 of the 3-service refactor (account was Phase 4, completed earlier). Both `auth` and `transfer` moved from package-by-layer to Clean Architecture / DDD 3-layer.

**Auth (Phase 2):**
- **domain/** — User POJO; interfaces: UserRepository, TokenService, SessionStore; framework-free.
- **application/** — AuthService use-case; DTOs: LoginRequest, LoginResponse.
- **infrastructure/** — AuthController, JpaEntity/SpringData layer, UserMapper (MapStruct), JwtTokenService (impl TokenService), RedisSessionStore (impl SessionStore), KafkaConfig.
- **Key decision:** split the original single TokenService (did BOTH JWT generation + Redis session check) into two: JwtTokenService (handles JWT signing/verification—impl domain TokenService interface) + RedisSessionStore (handles Redis read/write—impl domain SessionStore interface). AuthService.verify() orchestrates the flow: parse JWT → validate signature → check Redis session. I7 (revocable session: valid = signature OK AND session exists) verified preserved exactly — code review built truth table, confirmed no auth bypass.

**Transfer (Phase 3):**
- **domain/** — Transfer, TransferStatus enum, AccountView aggregates; interfaces: TransferRepository, OutboxRepository, InboxRepository, AccountClient; exceptions: DuplicateIdempotencyException, InsufficientFundsException, etc.
- **application/** — TransferService use-case; DTOs.
- **infrastructure/** — TransferController, AccountGrpcClient, OutboxRelay, TransferResultConsumer, SpringData persistence layer, KafkaConfig, TransferMapper.
- **Key decision:** idempotency race (I3) — TransferRepositoryImpl.save wraps DataIntegrityViolationException → domain DuplicateIdempotencyException; service catches → re-reads existing transfer. Handles the "save failed, is it ours?" edge case.

## The Brutal Truth

**Auth:** Straightforward. The split felt right—SessionStore is a clear domain concept (can be swapped for DB, file, etc.). No surprises.

**Transfer:** Code review surfaced a fragility that felt like a near-miss. The idempotency handler catches DataIntegrityViolationException synchronously, BUT that only works because createInTransaction's @Transactional is bypassed by Spring self-invocation (no ambient tx → SQLs execute synchronously, unique violation surfaces immediately in the try/catch). If someone later wraps createInTransaction in an outer @Transactional, the unique violation would be deferred to commit—the catch wouldn't fire, and exception handling would break. **Not a bug today, but a trap.** Fixed with saveAndFlush so unique violations always surface eagerly, no behavior change now, robust if ambient tx ever added.

The frustrating part: this fragility existed at HEAD (identical code pre-refactor). We caught it during the refactor review, but it's a pre-existing issue (M1) that spans both atomicity + exception handling. Not in scope to fix now, but it's a landmine that M1 documents. Feels like skirted disaster rather than clean win.

## Technical Details

**Auth invariants preserved:**
- I7 (revocable session check): signature + Redis lookup both required.

**Transfer invariants preserved:**
- I3 (idempotency/race): Idempotency-Key + unique constraint + saveAndFlush + exception catch = safe.
- I4 (inbox dedup): Processed messages dedup on consumer side.
- I5 (gRPC deadlines): 5s timeout, UNAVAILABLE/DEADLINE → 503 in controller.
- I6 (outbox at-least-once): relay blocks on broker ack.
- I8 (RFC7807 error contract): 403/404/503/400 unchanged.
- I9 (OpenAPI spec): generated + diffed vs baseline = EMPTY (preserved).

**Build verification:**
- `mvn clean package` (both services) → BUILD SUCCESS.
- ArchUnit 3/3 rules passing each (infrastructure→application→domain, domain framework-free).
- E2E smoke: 19/19 passing.
- `pnpm generate:api` (frontend) + diff against checked-in spec = EMPTY (I9).

**Commits:**
- Auth: `3a6d442`, transfer: `3196d6e`. Branch: `refactor/account-clean-architecture`. Conventional format, no AI references.

## What We Tried

1. **Auth:** Original monolithic TokenService felt cluttered. Split into JwtTokenService + RedisSessionStore from day one. No reversions. Review endorsed immediately.
2. **Transfer:** Idempotency exception handling—first attempt caught exception but re-read was async (in a separate @Transactional). Changed to saveAndFlush in the same transaction, re-read after catch. Cleaner flow, more predictable timing.

## Root Cause Analysis

Same as Phase 4 (account): package-by-layer mixed domain logic with Spring plumbing. Refactor isolated domain interfaces (UserRepository, TokenService, SessionStore, TransferRepository, etc.) from infrastructure (Spring Data, JPA, gRPC clients, Kafka). Payoff: domain objects testable in isolation. Risk: mapping layer + client/server abstractions add indirection—bugs hide if mappers not validated.

The M1 issue (transfer outbox non-atomicity pre-existing) is a separate design debt: transfer INSERT + outbox INSERT not atomic if crash between them. Flagged for a future decision (separate PR/issue), not a refactor regression.

## Lessons Learned

1. **Splitting responsibilities at the domain level pays off.** TokenService → JwtTokenService + RedisSessionStore is clearer than a monolith pretending to do two unrelated things. Code review saw it immediately.
2. **Eager exception surfacing beats deferred logic.** saveAndFlush fixed a latent trap. Future: use it by default in create paths where idempotency matters.
3. **Pre-existing issues surface during refactor.** M1 (non-atomic outbox) existed before we touched transfer. Refactor didn't create it, but review caught it. Document, separate, don't conflate with refactor scope.
4. **Invariants are the north star.** Structured auth split + transfer exception handling around I3/I7 needs, not around "what's architecturally pretty."

## Next Steps

1. **Phase 5: documentation update.** Reflect 3-layer structure in `docs/codebase-summary.md`, link to existing invariant docs (events, db schema, gRPC).
2. **M1 decision:** outbox non-atomicity—file as a separate tech-debt issue. Propose fix (transactional outbox producer, possibly via Debezium or manual flush order) for a separate PR.
3. **CI integration:** add ArchUnit to GitHub Actions phase-gate.
4. **PR review:** merge `refactor/account-clean-architecture` (all 3 services) as one cohesive commit sequence. Notify team of new structure, point to phase journals for context.

---

**Unresolved:**
- **M1:** transfer.created + outbox.transfer_requested not atomic if crash between rows. Pre-existing, out of refactor scope. Separate decision needed: is the risk acceptable, or do we need transactional outbox? Would also let H1's saveAndFlush be revisited.
- **Phase 5 docs:** update in progress; whole refactor not yet pushed.
