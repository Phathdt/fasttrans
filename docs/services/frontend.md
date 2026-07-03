# Frontend (React/Vite)

Minimal SPA to demo the flows: login, list transfers, create transfer (pick a source account), and view detail with status polling. All calls go through Traefik under `/api/*`; the SPA is served same-origin behind Traefik to avoid CORS.

- Plan phase: [phase-06-react-fe.md](../../plans/260703-1537-java-microservices-transfer-demo/phase-06-react-fe.md)
- Stack: React 18 + Vite + TypeScript, react-router, plain fetch. Served by nginx behind Traefik.

## Pages / routes

| Route | Purpose | Calls |
|---|---|---|
| `/login` | Login form | `POST /api/auth/login` |
| `/transfers` | List transfers, link to detail, "New" button | `GET /api/transfers` |
| `/transfers/new` | Create form; pick `fromAccountRef` from a dropdown, type `toAccountRef` (paste seed ref) | `GET /api/accounts`, `POST /api/transfers` |
| `/transfers/:id` | Detail, polls every 1.5s until status ≠ PENDING | `GET /api/transfers/{id}` |

## Auth handling

- Store JWT in localStorage; attach `Authorization: Bearer <token>` on protected calls.
- On any `401` → clear token and redirect to `/login`.

## Create transfer

1. Fetch `GET /api/accounts` → populate the source-account dropdown (the user's N accounts, valued by `accountRef`).
2. Generate an `Idempotency-Key` (`crypto.randomUUID()`) held in component state; reuse it on retry.
3. `POST /api/transfers` with header `Idempotency-Key` and body `{ fromAccountRef, toAccountRef, amount, currency }` (`toAccountRef` typed as text — paste a seed ref).
4. Disable submit while pending. On `403` (not owned) show an error. On `201` navigate to the detail page.

## Detail polling

`GET /api/transfers/{id}` on an interval (1.5s); stop when `COMPLETED`/`FAILED`, show `reason` on failure. Cap poll attempts and surface a timeout note if it stays PENDING.

## Serving

- Multi-stage Dockerfile (node build → nginx). nginx serves the SPA with `index.html` fallback.
- Traefik routes `/` → frontend with lower priority than `/api` so API paths match first.

## Acceptance
- Login → token stored. Create page shows the user's accounts in the dropdown.
- Pick source account → create → PENDING → auto-updates to COMPLETED/FAILED via polling.
- List shows only the user's transfers. Expired token → auto-redirect to login on 401.
