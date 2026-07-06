# Documentation — Transfer System

Detailed docs per service, plus shared database, event, and gRPC contracts. Implementation plan lives under [`plans/260703-1537-java-microservices-transfer-demo/`](../plans/260703-1537-java-microservices-transfer-demo/plan.md).

## Services

| Service  | Doc                                                          | Interfaces                                                         |
| -------- | ------------------------------------------------------------ | ------------------------------------------------------------------ |
| Auth     | [services/auth-service.md](services/auth-service.md)         | REST (login, verify)                                               |
| Transfer | [services/transfer-service.md](services/transfer-service.md) | REST (transfers, accounts) · gRPC client · Kafka producer/consumer |
| Account  | [services/account-service.md](services/account-service.md)   | gRPC server · Kafka consumer/producer (no REST)                    |
| Frontend | [services/frontend.md](services/frontend.md)                 | React SPA via Traefik                                              |

## Shared contracts

- Database schema (all 3 DBs) + seed: [db/schema.md](db/schema.md)
- Kafka/Redpanda event schemas: [events/transfer-events.md](events/transfer-events.md)
- gRPC proto: `libs/fasttrans-grpc-contract/src/main/proto/account.proto` (created in Phase 1)

## Diagrams

- Architecture, sequences, state, outbox relay, ERD (Mermaid): [diagrams.md](diagrams.md)

## Architecture at a glance

```
Browser (React) → Traefik ──(ForwardAuth → auth /verify)── /api/transfers, /api/accounts → Transfer
                       └── /api/auth → Auth
Transfer ──gRPC(ValidateOwnership, ListAccounts)──▶ Account
Transfer ──Redpanda transfer.requested──▶ Account
Account  ──Redpanda transfer.result────▶ Transfer
```

- gRPC (sync): ownership validation + account listing.
- Redpanda (async): debit/credit + result, with Outbox (produce) and Inbox (consume) on both sides.

## Per-service databases

| Service  | Database      | Tables                                                       |
| -------- | ------------- | ------------------------------------------------------------ |
| Auth     | `auth_db`     | users                                                        |
| Transfer | `transfer_db` | transfers (with idempotency_key), outbox, processed_messages |
| Account  | `account_db`  | accounts, ledger_entries, processed_messages, outbox         |
