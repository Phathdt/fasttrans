# Auth Service

User authentication: login (JWT + Redis session) and token verification for Traefik ForwardAuth. Does not know about accounts (a user has N accounts, ownership is owned by the account service).

- Plan phase: [phase-02-auth-service.md](../../plans/260703-1537-java-microservices-transfer-demo/phase-02-auth-service.md)
- Stack: Spring Boot (Web, Data JPA, Data Redis), Postgres `auth_db`, Redis, jjwt, BCrypt.
- Port: HTTP 8080 (internal, exposed via Traefik).

## Database — auth_db

```sql
CREATE TABLE users (
    id            uuid PRIMARY KEY,
    username      varchar(50)  NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,          -- BCrypt
    created_at    timestamptz  NOT NULL DEFAULT now()
);
```

Seed (fixed UUIDs, see [db/schema.md](../db/schema.md)):
- alice `11111111-...-111111111111`, bob `22222222-...-222222222222`, password `password`.

No `account_id` column. JWT carries only `sub = userId`.

## Session store — Redis

- Key `session:<token>` → value `userId`, TTL = JWT expiry (24h for the demo).
- Verify = valid JWT signature AND key still present in Redis → enables real revocation (delete key).

## REST API

Routed via Traefik: `/api/auth/*` (strip `/api` → controller mounts `/auth`). No ForwardAuth.

### POST /auth/login
Authenticate, return JWT and create a Redis session.

Request:
```json
{ "username": "alice", "password": "password" }
```
Response `200`:
```json
{ "token": "<jwt>", "expiresIn": 86400 }
```
Errors: `401` invalid username/password.

### GET /auth/verify
Endpoint called by Traefik ForwardAuth. Checks token validity.

Request header: `Authorization: Bearer <jwt>`
- `200` + header `X-User-Id: <uuid>` when valid (does NOT return account_id).
- `401` when token has bad signature, is expired, or was revoked (Redis key gone).

## No gRPC / Kafka
Auth is HTTP only. No producers/consumers, no gRPC.

## Acceptance
- Login seeded user → 200 + token; wrong password → 401.
- Verify valid token → 200 + X-User-Id; expired/revoked → 401.
