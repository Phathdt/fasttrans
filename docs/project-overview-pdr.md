# FastTrans Project Overview & PDR

## Project Purpose

FastTrans is a demonstration money-transfer system designed to showcase cloud-native architectural patterns: event-driven messaging, synchronous RPC, domain-driven design, and clean architecture principles across a microservices topology.

**Target Use**: Educational reference for teams building scalable financial transaction systems.

## Product Definition

### Core Capability
Enable authenticated users to initiate money transfers between their own accounts with:
- Real-time ownership validation via gRPC
- Asynchronous ledger updates with guaranteed consistency
- Idempotent API semantics (replay-safe)
- Double-entry accounting with balance reconciliation

### Key Features
1. **User Authentication** (Auth Service)
   - JWT-based tokens with Redis session revocation
   - Traefik ForwardAuth integration for transparent token validation
   - Password: BCrypt hashing

2. **Transfer Lifecycle** (Transfer Service)
   - Ownership validation (gRPC sync)
   - Transactional Outbox pattern for event reliability
   - Idempotency-Key support (UUID replay protection)
   - Transfer status polling (PENDING → COMPLETED/FAILED)

3. **Ledger & Accounts** (Account Service)
   - Double-entry ledger with balance snapshots
   - Inbox dedup pattern for Kafka consumer idempotency
   - gRPC server for ownership verification and account listing
   - No REST interface (intentional; async-only contract)

4. **Frontend** (React SPA)
   - Type-safe API client (orval + zod + react-query)
   - Account and transfer management UI
   - Real-time transfer status polling

### Scope (In)
- Money transfers between user's own accounts
- Multi-currency support (schema accepts any 3-letter code; demo uses VND)
- Microservices communication patterns (gRPC + Kafka)
- Event sourcing via Transactional Outbox + Inbox
- Account reconciliation (balance = sum of ledger)

### Scope (Out)
- Recipient management (transfers only to existing accounts)
- Wire transfers / inter-bank routing
- Compliance/KYC/AML screening
- High-frequency trading optimization
- Mobile app
- Production hardening (Traefik dashboard, secrets management, etc. are intentionally insecure for demo)

## Technical Requirements

### Functional Requirements

| Req ID | Requirement | Priority | Status |
|--------|-------------|----------|--------|
| F1 | User login with JWT token generation | MUST | ✓ Implemented |
| F2 | Token verification via ForwardAuth endpoint | MUST | ✓ Implemented |
| F3 | List user's accounts (gRPC + REST proxy) | MUST | ✓ Implemented |
| F4 | Create transfer with ownership validation | MUST | ✓ Implemented |
| F5 | Idempotent transfer creation (Idempotency-Key) | MUST | ✓ Implemented |
| F6 | Transfer status tracking (PENDING → COMPLETED/FAILED) | MUST | ✓ Implemented |
| F7 | Double-entry ledger with balance reconciliation | MUST | ✓ Implemented |
| F8 | Async debit/credit via Kafka | MUST | ✓ Implemented |
| F9 | Session revocation (Redis key deletion) | SHOULD | ✓ Implemented |

### Non-Functional Requirements

| Category | Requirement | Target | Status |
|----------|-------------|--------|--------|
| **Availability** | Services healthy within 60s startup | 99% container readiness | ✓ Flyway + actuator |
| **Consistency** | No ledger balance drift (sum = balance) | 100% reconciliation on every entry | ✓ Double-entry validated |
| **Reliability** | At-least-once event delivery | Transfer result replay-safe | ✓ Inbox dedup |
| **Idempotency** | Transfer replays are no-op | Same Idempotency-Key = same transfer | ✓ Unique constraint + logic |
| **Performance** | Transfer e2e latency | <5s typical (1s Kafka poll delay) | ✓ E2E smoke test ~2-4s |
| **Architecture** | Clean Architecture + DDD | All services 3-layer (domain/app/infra) | ✓ ArchUnit enforced |
| **Testing** | ArchUnit enforcement + integration tests | All layers + event flow | ✓ 19 smoke checks |

## Acceptance Criteria

### Transfer Creation
- [x] Valid ownership (gRPC check) → transfer inserted, outbox row created, transfer.requested published
- [x] Invalid ownership → 403 Forbidden, no DB write
- [x] Missing Idempotency-Key → 400 Bad Request
- [x] Replay same key → returns original transfer, no duplicate
- [x] Account service down → 503 Service Unavailable

### Ledger & Balance
- [x] Sufficient funds → COMPLETED, 2 ledger rows (DEBIT + CREDIT), balances updated
- [x] Insufficient funds → FAILED, 0 ledger rows, balances unchanged
- [x] Replay same transfer.requested → no extra ledger entry, balance unchanged
- [x] sum(ledger) per account == balance (always)

### API Contracts
- [x] POST /auth/login returns JWT + expiresIn
- [x] GET /auth/verify (ForwardAuth) validates token, injects X-User-Id
- [x] POST /transfers (idempotent) creates transfer with Idempotency-Key
- [x] GET /transfers lists user's transfers with status
- [x] GET /accounts lists user's accounts via gRPC

### Architecture
- [x] All 3 services follow 3-layer architecture (domain/application/infrastructure)
- [x] ArchUnit enforces dependency rule: infrastructure → application → domain
- [x] Domain entities are framework-free POJOs
- [x] Kafka messaging uses Transactional Outbox + Inbox dedup
- [x] gRPC calls are synchronous with 5s timeout

## Implementation Status

### Completed Phases
- Phase 1: gRPC proto + infra setup (Postgres, Redis, Redpanda, Traefik, Docker Compose)
- Phase 2: Auth service (REST login/verify, JWT + Redis sessions)
- Phase 3: Transfer service (REST API, gRPC client, Kafka producer/consumer, Outbox relay)
- Phase 4: Account service (gRPC server, ledger, Kafka consumer, Outbox relay)
- Phase 5: Frontend (React SPA, orval-generated API client)
- Phase 6: Clean Architecture refactor (all 3 services moved from package-by-layer to 3-layer with ArchUnit)

### Known Limitations
1. **Single-replica**: Each service runs 1 instance; Outbox relay `FOR UPDATE SKIP LOCKED` is defensive but unused
2. **Polling relay**: 1s poll interval trades latency for simplicity (CDC/Debezium out of scope)
3. **No gRPC retries**: 5s deadline on gRPC calls; no exponential backoff or circuit breaker
4. **Auth token no refresh**: 24h expiry; no refresh token or session sliding window
5. **Account immutable**: No account closure, rename, or currency change
6. **No transfer reversal**: Completed transfers cannot be undone (by design)

## Success Metrics

| Metric | Target | Current |
|--------|--------|---------|
| Build Success Rate | 100% | ✓ mvn clean package all 3 |
| ArchUnit Violations | 0 | ✓ 3/3 pass |
| E2E Smoke Tests | 19/19 pass | ✓ All pass |
| Ledger Reconciliation | 100% accuracy | ✓ Verified |
| Transfer Latency (e2e) | <5s | ✓ ~2-4s observed |
| API Error Handling | All documented | ✓ 400/403/503/404 codes |
| Code Coverage (ideal) | >80% | ⚠ Unit tests only; integration heavy |

## Deployment Model

### Infrastructure
- **Containerization**: Docker Compose (all services, DBs, messaging, reverse proxy)
- **Orchestration**: None (docker compose; Kubernetes out of scope)
- **Reverse Proxy**: Traefik (ForwardAuth integration, path-based routing)
- **Messaging**: Redpanda (Kafka-compatible for demo; production → AWS MSK or Confluent)
- **Session Store**: Redis (in-memory; production → managed Redis or DynamoDB)
- **Databases**: PostgreSQL (3 schemas; production → RDS or multi-region replicas)

### Build Pipeline
```
Source (git) → Docker Compose (build all) → Integration tests (mvn verify)
             → E2E smoke tests (19 checks) → Manual acceptance / deploy
```

No CI/CD pipelines defined (demo repo). Production would require: GitHub Actions / GitLab CI with lint, unit test, integration test, Docker build, registry push, and deploy stages.

## Version & History

| Date | Version | Changes |
|------|---------|---------|
| 2026-07-04 | 1.0.0 | All 3 services refactored to Clean Architecture; frontend integrated; 19/19 e2e smoke pass |

## Next Steps / Roadmap

### Short Term (Production Ready)
1. CI/CD pipeline (GitHub Actions: lint, test, build, push, deploy)
2. Distributed tracing (Jaeger or Datadog)
3. Metrics + alerting (Prometheus, Grafana)
4. gRPC retries + circuit breaker (Resilience4j)
5. Secrets management (.env → AWS Secrets Manager or Vault)

### Medium Term (Scaling)
1. Multi-instance relay + consensus (only 1 service publishes per partition)
2. Read replicas for account queries (gRPC to read-only replica)
3. Kafka consumer groups + parallel processing
4. API rate limiting + per-user quotas

### Long Term (Feature Expansion)
1. Recipient management (whitelist + transfer rules)
2. Scheduled transfers (cron-based)
3. Multi-currency settlement (FX rates, rounding)
4. Dispute resolution workflow (freeze + manual review)

## Risk Assessment

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|-----------|
| **Ledger race condition** | Balance drift | Low | SELECT FOR UPDATE + transaction boundary |
| **Duplicate message processing** | Double-debit | Low | Inbox dedup on messageId PK |
| **Outbox stuck PENDING** | Transfer hangs | Low | Relay polling; manual inspection via SQL |
| **gRPC timeout** | 503 error | Medium | 5s deadline; transfer rolls back; client retries with Idempotency-Key |
| **Redis flush** | Session loss | Medium | Log in again; app graceful redirect to login |
| **Account service down** | Transfer create blocked | High | Dependent on service health; circuit breaker recommended |

## Compliance & Security Notes

**Intentionally Insecure (Demo Only)**:
- Traefik dashboard exposed (should require auth)
- Redis no password (should require AUTH)
- JWT secret stored in env (should use Vault)
- Postgres user/password in docker-compose.yml (should use secrets manager)
- No HTTPS (should enforce TLS in production)

**Compliant / Secure**:
- Passwords BCrypt-hashed (OWASP recommendation)
- Session tokens revocable (Redis-backed)
- Idempotency keys prevent replay (safe for retries)
- Double-entry ledger = accounting standard
- No balance underflow (CHECK constraint)

**Audit Trail**:
- All ledger entries immutable (INSERT only, no UPDATE/DELETE)
- Transfers timestamped (created_at, updated_at)
- Outbox events logged (status, sent_at for observability)

## Contact & Handoff

**Owner**: Demo project (no active owner)  
**Documentation**: See `/docs/` directory  
**Code**: Source at `services/` (auth, transfer, account) + `frontend/`  
**Contracts**: Events, DB schema, gRPC proto in `/docs/` + `proto/`
