# FastTrans — Demo Transfer System (Java Spring Boot Microservices)

Demo hệ thống chuyển tiền event-driven + sync gRPC. 3 service Java (auth, transfer, account) + React/Vite FE, chạy toàn bộ qua `docker compose`.

## Kiến trúc tóm tắt

```
Browser (React/Vite)
      │
      ▼
   Traefik ── ForwardAuth ──▶ auth /auth/verify (Redis session check)
   │      │
   ▼      ▼
 auth   transfer ── gRPC ValidateOwnership / ListAccounts ──▶ account (gRPC server + Kafka consumer)
          │  ▲                                                   │
   publish│  │ transfer.result                    transfer.requested │
          ▼  │                                                   ▼
        [ Redpanda ]  ◀──────────────────────────────────────────
```

- **gRPC (sync)**: transfer → account — validate ownership khi tạo transfer + list accounts.
- **Redpanda (async)**: transfer ↔ account — debit/credit + result (Transactional Outbox + Inbox dedup).
- **Traefik ForwardAuth**: mọi request tới transfer đi qua auth `/auth/verify`, inject `X-User-Id`.

## Chạy

```bash
docker compose up --build      # dựng toàn bộ; chờ tất cả healthy
docker compose down -v         # dừng + xóa volume (reset sạch state)
```

## Ports

| Service   | Port (host) | Ghi chú                          |
|-----------|-------------|----------------------------------|
| Traefik   | 80          | Gateway, mọi `/api/*` + FE `/`   |
| Traefik dashboard | 8081 | Demo only (insecure)             |
| Postgres  | 5432        | user/pass `fasttrans`; 3 db      |
| Redis     | 6379        | session store auth               |
| Redpanda  | 9092        | Kafka API (host); 29092 nội bộ   |
| account   | 9090        | gRPC nội bộ (không expose HTTP)  |

Nếu cổng `80`/`5432`/`9092` bị chiếm bởi process local → sửa mapping `ports:` trong `docker-compose.yml`.

## Seed data

| User  | Password   | Account ref     | Balance (VND) |
|-------|------------|-----------------|---------------|
| alice | `password` | `100000000001`  | 1,000,000     |
| alice | `password` | `100000000002`  | 50,000        |
| bob   | `password` | `200000000001`  | 0             |

Tiền lưu `bigint` đơn vị nhỏ nhất (VND: 1 = 1đ). Account tham chiếu bằng `accountRef` (12 chữ số public), UUID chỉ dùng nội bộ account_db.

## API (qua Traefik `/api`)

| Method | Path                  | Auth        | Mô tả                                  |
|--------|-----------------------|-------------|----------------------------------------|
| POST   | `/api/auth/login`     | —           | `{username,password}` → `{token}`      |
| GET    | `/api/auth/verify`    | Bearer      | ForwardAuth endpoint (nội bộ)          |
| GET    | `/api/accounts`       | ForwardAuth | list account của user (gRPC)           |
| POST   | `/api/transfers`      | ForwardAuth | tạo transfer (header `Idempotency-Key`)|
| GET    | `/api/transfers`      | ForwardAuth | list transfer của user                 |
| GET    | `/api/transfers/{id}` | ForwardAuth | detail 1 transfer                      |

## Contracts

- Event schema: [docs/events/transfer-events.md](docs/events/transfer-events.md)
- DB schema + seed: [docs/db/schema.md](docs/db/schema.md)
- gRPC proto: [proto/account.proto](proto/account.proto)
- Docs index: [docs/README.md](docs/README.md)

## Demo walkthrough

Xem [Phase 7](plans/260703-1537-java-microservices-transfer-demo/phase-07-e2e-verify.md) và `scripts/e2e-smoke.sh` sau khi build xong.
