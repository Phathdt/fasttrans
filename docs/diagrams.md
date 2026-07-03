# Diagrams

Mermaid diagrams for the transfer system. Renders on GitHub and any Mermaid-aware viewer.

- Plan: [../plans/260703-1537-java-microservices-transfer-demo/plan.md](../plans/260703-1537-java-microservices-transfer-demo/plan.md)
- Services: [auth](services/auth-service.md) · [transfer](services/transfer-service.md) · [account](services/account-service.md) · [frontend](services/frontend.md)

## 1. System architecture (C4-ish container view)

```mermaid
flowchart TB
    browser["React SPA (Vite)"]

    subgraph gw["Gateway"]
        traefik["Traefik v3<br/>ForwardAuth middleware"]
    end

    subgraph svc["Services"]
        auth["Auth Service<br/>REST"]
        transfer["Transfer Service<br/>REST + gRPC client + Kafka"]
        account["Account Service<br/>gRPC server + Kafka"]
    end

    subgraph infra["Infrastructure"]
        redis[("Redis<br/>sessions")]
        pgAuth[("auth_db")]
        pgTransfer[("transfer_db")]
        pgAccount[("account_db")]
        redpanda{{"Redpanda<br/>transfer.requested / transfer.result"}}
    end

    browser -->|HTTP /api/*| traefik
    traefik -->|/api/auth/*| auth
    traefik -->|"/api/transfers/*, /api/accounts<br/>(after ForwardAuth)"| transfer
    traefik -.->|verify token| auth

    auth --> redis
    auth --> pgAuth

    transfer -->|"gRPC ValidateOwnership / ListAccounts"| account
    transfer --> pgTransfer
    account --> pgAccount

    transfer -->|produce transfer.requested| redpanda
    redpanda -->|consume| account
    account -->|produce transfer.result| redpanda
    redpanda -->|consume| transfer
```

## 2. Login & token verification

```mermaid
sequenceDiagram
    autonumber
    actor U as Browser
    participant T as Traefik
    participant A as Auth
    participant R as Redis
    participant DB as auth_db

    U->>T: POST /api/auth/login {username, password}
    T->>A: forward
    A->>DB: load user by username
    A->>A: BCrypt verify password
    A->>A: issue JWT (sub=userId)
    A->>R: SET session:<token> = userId (TTL)
    A-->>U: 200 {token, expiresIn}

    Note over U,A: subsequent protected call
    U->>T: GET /api/transfers (Bearer token)
    T->>A: ForwardAuth GET /auth/verify
    A->>R: EXISTS session:<token>
    alt valid
        A-->>T: 200 + X-User-Id
        T->>T: inject X-User-Id, route to Transfer
    else expired / revoked
        A-->>T: 401
        T-->>U: 401 (Transfer never called)
    end
```

## 3. Create transfer — sync validate + async settlement

```mermaid
sequenceDiagram
    autonumber
    actor U as Browser
    participant T as Traefik
    participant TR as Transfer
    participant AC as Account
    participant K as Redpanda
    participant TDB as transfer_db
    participant ADB as account_db

    U->>T: POST /api/transfers (Idempotency-Key, X-User-Id)
    T->>TR: forward (after ForwardAuth)
    TR->>AC: gRPC ValidateOwnership(userId, fromAccountRef)
    alt not owned
        AC-->>TR: owned=false
        TR-->>U: 403
    else owned
        AC-->>TR: owned=true
        TR->>TDB: BEGIN — insert transfer PENDING + insert outbox (transfer.requested)
        TDB-->>TR: COMMIT
        TR-->>U: 201 {id, status: PENDING}
    end

    Note over TR,K: outbox relay (@Scheduled, ~1s)
    TR->>K: publish transfer.requested (key=fromAccountRef)
    K->>AC: consume transfer.requested
    AC->>ADB: BEGIN — dedup(messageId)? debit/credit ledger + balances + insert outbox(result)
    ADB-->>AC: COMMIT
    Note over AC,K: outbox relay
    AC->>K: publish transfer.result (COMPLETED/FAILED)
    K->>TR: consume transfer.result
    TR->>TDB: dedup(messageId)? update status if PENDING

    loop poll until status != PENDING
        U->>T: GET /api/transfers/{id}
        T->>TR: forward
        TR-->>U: {status: PENDING|COMPLETED|FAILED}
    end
```

## 4. Transfer status lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING: create (owned + persisted)
    PENDING --> COMPLETED: transfer.result COMPLETED
    PENDING --> FAILED: transfer.result FAILED<br/>(INSUFFICIENT_FUNDS / ACCOUNT_NOT_FOUND)
    COMPLETED --> [*]
    FAILED --> [*]
    note right of PENDING
        result consumed once (inbox dedup on messageId);
        update applied only while still PENDING
    end note
```

## 5. Transactional Outbox relay (polling, not CDC)

```mermaid
flowchart LR
    subgraph tx["Business transaction (one commit)"]
        biz["write business row<br/>(transfer / ledger)"] --> ob[("outbox row<br/>status=PENDING")]
    end

    relay["@Scheduled relay (~1s)<br/>SELECT ... FOR UPDATE SKIP LOCKED"]
    ob --> relay
    relay -->|"send().get() (await ack)"| topic{{"Redpanda topic"}}
    relay -->|on ack| mark[("UPDATE outbox<br/>status=SENT")]

    topic --> consumer["consumer"]
    consumer --> inbox[("processed_messages<br/>dedup on messageId")]

    relay -. crash after ack, before SENT .-> topic
    note1["re-send on next loop → inbox absorbs duplicate<br/>(at-least-once)"]
    inbox -.-> note1
```

## 6. Data model (per-service databases)

```mermaid
erDiagram
    USERS {
        uuid id PK
        varchar username UK
        varchar password_hash
        timestamptz created_at
    }
    TRANSFERS {
        uuid id PK
        uuid user_id
        text idempotency_key
        text from_account_ref
        text to_account_ref
        bigint amount
        varchar currency
        varchar status
        varchar reason
    }
    ACCOUNTS {
        uuid id PK
        text account_ref UK
        uuid user_id
        varchar owner_name
        bigint balance
        varchar currency
    }
    LEDGER_ENTRIES {
        uuid id PK
        uuid account_id FK
        uuid transfer_id
        varchar direction
        bigint amount
        bigint balance_after
    }

    ACCOUNTS ||--o{ LEDGER_ENTRIES : "has entries"
    USERS ||..o{ TRANSFERS : "initiates (cross-db, logical)"
    USERS ||..o{ ACCOUNTS : "owns (cross-db, logical)"
    TRANSFERS ||..o{ LEDGER_ENTRIES : "settled as (cross-db, logical)"
```

Dashed relations are cross-database logical links (no physical FK across service DBs). `transfers.(user_id, idempotency_key)` is UNIQUE for API idempotency. Both transfer_db and account_db also hold `outbox` and `processed_messages` (omitted above for clarity — see [db/schema.md](db/schema.md)).
