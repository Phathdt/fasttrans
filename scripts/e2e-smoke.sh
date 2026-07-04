#!/usr/bin/env bash
# e2e-smoke.sh — end-to-end test of the FastTrans system through the Traefik gateway.
# Requires: docker compose up --build has run and all services are healthy.
# Usage: bash scripts/e2e-smoke.sh
set -euo pipefail

BASE="http://localhost:4000"
PASS=0
FAIL=0

green()  { echo -e "\033[0;32m[PASS]\033[0m $*"; ((PASS++)) || true; }
red()    { echo -e "\033[0;31m[FAIL]\033[0m $*"; ((FAIL++)) || true; }
info()   { echo -e "\033[0;36m[INFO]\033[0m $*"; }
section(){ echo; echo -e "\033[1m=== $* ===\033[0m"; }

require() {
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "Required: $cmd not found"; exit 1; }
  done
}
require curl jq

# ── 1. Auth ──────────────────────────────────────────────────────────
section "1. Login"

ALICE_TOKEN=$(curl -sf -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}' | jq -r '.token')
[ -n "$ALICE_TOKEN" ] && green "alice login OK" || { red "alice login FAILED"; exit 1; }

BOB_TOKEN=$(curl -sf -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"password"}' | jq -r '.token')
[ -n "$BOB_TOKEN" ] && green "bob login OK" || { red "bob login FAILED"; exit 1; }

# ── 2. Auth gate ─────────────────────────────────────────────────────
section "2. Auth gate (no token → 401)"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/transfers")
[ "$STATUS" = "401" ] && green "GET /transfers no token → 401" \
                       || red "Expected 401, got $STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/accounts")
[ "$STATUS" = "401" ] && green "GET /accounts no token → 401" \
                       || red "Expected 401, got $STATUS"

# ── 3. GET /accounts (account service) ──────────────────────────────
section "3. GET /accounts (account service)"

ALICE_ACCOUNTS=$(curl -sf "$BASE/accounts" \
  -H "Authorization: Bearer $ALICE_TOKEN")
ALICE_COUNT=$(echo "$ALICE_ACCOUNTS" | jq 'length')
[ "$ALICE_COUNT" = "2" ] && green "alice has 2 accounts" \
                          || red "Expected 2, got $ALICE_COUNT"

ALICE_REFS=$(echo "$ALICE_ACCOUNTS" | jq -r '.[].accountRef')
echo "$ALICE_REFS" | grep -q "100000000001" && green "alice A1 ref present" \
                                             || red "alice A1 100000000001 missing"
echo "$ALICE_REFS" | grep -q "100000000002" && green "alice A2 ref present" \
                                             || red "alice A2 100000000002 missing"

BOB_ACCOUNTS=$(curl -sf "$BASE/accounts" \
  -H "Authorization: Bearer $BOB_TOKEN")
BOB_COUNT=$(echo "$BOB_ACCOUNTS" | jq 'length')
[ "$BOB_COUNT" = "1" ] && green "bob has 1 account" \
                        || red "Expected 1, got $BOB_COUNT"

# GET /accounts/{ref} — lookup by accountRef
ALICE_REF="100000000001"
LOOKUP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/accounts/$ALICE_REF" \
  -H "Authorization: Bearer $ALICE_TOKEN")
[ "$LOOKUP_STATUS" = "200" ] && green "GET /accounts/$ALICE_REF → 200" \
                              || red "Expected 200, got $LOOKUP_STATUS"

LOOKUP_BODY=$(curl -sf "$BASE/accounts/$ALICE_REF" \
  -H "Authorization: Bearer $ALICE_TOKEN")
OWNER_NAME=$(echo "$LOOKUP_BODY" | jq -r '.ownerName // empty')
[ -n "$OWNER_NAME" ] && green "GET /accounts/$ALICE_REF has ownerName=$OWNER_NAME" \
                      || red "GET /accounts/$ALICE_REF missing ownerName"

MISSING_REF="999999999999"
MISSING_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/accounts/$MISSING_REF" \
  -H "Authorization: Bearer $ALICE_TOKEN")
[ "$MISSING_STATUS" = "404" ] && green "GET /accounts/$MISSING_REF → 404" \
                               || red "Expected 404, got $MISSING_STATUS"

# ── 4. gRPC ValidateOwnership guard ──────────────────────────────────
section "4. ValidateOwnership — alice uses bob's account → 403"

IDEM_403=$(python3 -c "import uuid; print(uuid.uuid4())")
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/transfers" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Idempotency-Key: $IDEM_403" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountRef":"200000000001","toAccountRef":"100000000002","amount":1000,"currency":"VND"}')
[ "$STATUS" = "403" ] && green "alice using bob's account → 403" \
                       || red "Expected 403, got $STATUS"

# ── 5. Missing Idempotency-Key → 400 ─────────────────────────────────
section "5. Missing Idempotency-Key → 400"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/transfers" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountRef":"100000000001","toAccountRef":"200000000001","amount":1000,"currency":"VND"}')
[ "$STATUS" = "400" ] && green "Missing Idempotency-Key → 400" \
                       || red "Expected 400, got $STATUS"

# ── 6. Create transfer — sufficient funds ────────────────────────────
section "6. Create transfer sufficient funds → 201 PENDING"

IDEM_OK=$(python3 -c "import uuid; print(uuid.uuid4())")
CREATE_RESP=$(curl -sf -X POST "$BASE/transfers" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Idempotency-Key: $IDEM_OK" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountRef":"100000000001","toAccountRef":"200000000001","amount":100000,"currency":"VND"}')
TRANSFER_ID=$(echo "$CREATE_RESP" | jq -r '.id')
INIT_STATUS=$(echo "$CREATE_RESP" | jq -r '.status')

[ -n "$TRANSFER_ID" ] && green "Transfer created id=$TRANSFER_ID" \
                       || { red "Transfer create failed: $CREATE_RESP"; exit 1; }
[ "$INIT_STATUS" = "PENDING" ] && green "Initial status = PENDING" \
                                || red "Expected PENDING, got $INIT_STATUS"

# Poll until COMPLETED/FAILED (up to 15s)
section "6b. Poll transfer detail → COMPLETED"

FINAL_STATUS="PENDING"
for i in $(seq 1 15); do
  sleep 1
  FINAL_STATUS=$(curl -sf "$BASE/transfers/$TRANSFER_ID" \
    -H "Authorization: Bearer $ALICE_TOKEN" | jq -r '.status')
  [ "$FINAL_STATUS" != "PENDING" ] && break
  info "Poll $i/15 still PENDING..."
done
[ "$FINAL_STATUS" = "COMPLETED" ] && green "Transfer → COMPLETED" \
                                   || red "Expected COMPLETED, got $FINAL_STATUS"

# ── 7. Idempotency — replay same key ─────────────────────────────────
section "7. API idempotency — replay Idempotency-Key"

REPLAY_RESP=$(curl -sf -X POST "$BASE/transfers" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Idempotency-Key: $IDEM_OK" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountRef":"100000000001","toAccountRef":"200000000001","amount":100000,"currency":"VND"}')
REPLAY_ID=$(echo "$REPLAY_RESP" | jq -r '.id')
[ "$REPLAY_ID" = "$TRANSFER_ID" ] && green "Replay returns same transfer id" \
                                   || red "Replay id mismatch: $REPLAY_ID != $TRANSFER_ID"

# ── 8. Insufficient funds ─────────────────────────────────────────────
section "8. Insufficient funds → FAILED"

IDEM_INSUF=$(python3 -c "import uuid; print(uuid.uuid4())")
INSUF_RESP=$(curl -sf -X POST "$BASE/transfers" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Idempotency-Key: $IDEM_INSUF" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountRef":"100000000002","toAccountRef":"200000000001","amount":999999999,"currency":"VND"}')
INSUF_ID=$(echo "$INSUF_RESP" | jq -r '.id')

INSUF_FINAL="PENDING"
for i in $(seq 1 15); do
  sleep 1
  DETAIL=$(curl -sf "$BASE/transfers/$INSUF_ID" \
    -H "Authorization: Bearer $ALICE_TOKEN")
  INSUF_FINAL=$(echo "$DETAIL" | jq -r '.status')
  INSUF_REASON=$(echo "$DETAIL" | jq -r '.reason // empty')
  [ "$INSUF_FINAL" != "PENDING" ] && break
  info "Poll $i/15 still PENDING..."
done
[ "$INSUF_FINAL" = "FAILED" ] && green "Insufficient funds → FAILED" \
                               || red "Expected FAILED, got $INSUF_FINAL"
[ "$INSUF_REASON" = "INSUFFICIENT_FUNDS" ] && green "reason = INSUFFICIENT_FUNDS" \
                                            || red "Expected INSUFFICIENT_FUNDS, got $INSUF_REASON"

# ── 9. Account not found ──────────────────────────────────────────────
section "9. toAccountRef does not exist → FAILED ACCOUNT_NOT_FOUND"

IDEM_NF=$(python3 -c "import uuid; print(uuid.uuid4())")
NF_RESP=$(curl -sf -X POST "$BASE/transfers" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Idempotency-Key: $IDEM_NF" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountRef":"100000000001","toAccountRef":"999999999999","amount":1000,"currency":"VND"}')
NF_ID=$(echo "$NF_RESP" | jq -r '.id')

NF_FINAL="PENDING"
for i in $(seq 1 15); do
  sleep 1
  NF_DETAIL=$(curl -sf "$BASE/transfers/$NF_ID" \
    -H "Authorization: Bearer $ALICE_TOKEN")
  NF_FINAL=$(echo "$NF_DETAIL" | jq -r '.status')
  NF_REASON=$(echo "$NF_DETAIL" | jq -r '.reason // empty')
  [ "$NF_FINAL" != "PENDING" ] && break
  info "Poll $i/15 still PENDING..."
done
[ "$NF_FINAL" = "FAILED" ] && green "ACCOUNT_NOT_FOUND → FAILED" \
                            || red "Expected FAILED, got $NF_FINAL"
[ "$NF_REASON" = "ACCOUNT_NOT_FOUND" ] && green "reason = ACCOUNT_NOT_FOUND" \
                                        || red "Expected ACCOUNT_NOT_FOUND, got $NF_REASON"

# ── 10. GET /transfers list ───────────────────────────────────────
section "10. GET /transfers lists user's transfers"

ALICE_TRANSFERS=$(curl -sf "$BASE/transfers" \
  -H "Authorization: Bearer $ALICE_TOKEN")
COUNT=$(echo "$ALICE_TRANSFERS" | jq 'length')
[ "$COUNT" -ge "1" ] && green "alice transfers list count=$COUNT" \
                      || red "Expected >=1 transfers, got $COUNT"

# ── Summary ───────────────────────────────────────────────────────────
echo
echo "────────────────────────────────────"
echo "  PASSED: $PASS"
echo "  FAILED: $FAIL"
echo "────────────────────────────────────"
[ "$FAIL" -eq 0 ] && echo -e "\033[0;32mAll checks passed.\033[0m" && exit 0 \
                  || echo -e "\033[0;31m$FAIL check(s) failed.\033[0m" && exit 1
