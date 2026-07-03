#!/usr/bin/env bash
# gen-openapi.sh
#
# Full pipeline: rebuild auth + transfer → wait healthy → fetch specs → merge into docs/openapi.yaml
# Run from project root:  bash scripts/gen-openapi.sh
# Or after chmod +x:      ./scripts/gen-openapi.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

AUTH_TMP="$(mktemp /tmp/fasttrans-auth-spec-XXXXXX.json)"
TRANSFER_TMP="$(mktemp /tmp/fasttrans-transfer-spec-XXXXXX.json)"

cleanup() {
  rm -f "$AUTH_TMP" "$TRANSFER_TMP"
}
trap cleanup EXIT

echo "[1/4] Rebuilding and starting auth + transfer services..."
docker compose -f "$PROJECT_ROOT/docker-compose.yml" up -d --build auth transfer

echo "[2/4] Waiting for services to be healthy (up to 120s)..."
for i in $(seq 1 24); do
  auth_up=$(docker compose -f "$PROJECT_ROOT/docker-compose.yml" exec -T auth \
    wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -c '"UP"' || true)
  transfer_up=$(docker compose -f "$PROJECT_ROOT/docker-compose.yml" exec -T transfer \
    wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -c '"UP"' || true)
  if [ "${auth_up}" -ge 1 ] && [ "${transfer_up}" -ge 1 ]; then
    echo "   Both services healthy."
    break
  fi
  if [ "$i" -eq 24 ]; then
    echo "ERROR: Services did not become healthy within 120s." >&2
    docker compose -f "$PROJECT_ROOT/docker-compose.yml" logs --tail=40 auth transfer >&2
    exit 1
  fi
  echo "   Waiting... ($((i * 5))s / 120s)"
  sleep 5
done

echo "[3/4] Fetching OpenAPI specs from containers..."
docker compose -f "$PROJECT_ROOT/docker-compose.yml" exec -T auth \
  wget -qO- http://localhost:8080/v3/api-docs > "$AUTH_TMP"
docker compose -f "$PROJECT_ROOT/docker-compose.yml" exec -T transfer \
  wget -qO- http://localhost:8080/v3/api-docs > "$TRANSFER_TMP"

echo "   auth spec   : $(wc -c < "$AUTH_TMP") bytes"
echo "   transfer spec: $(wc -c < "$TRANSFER_TMP") bytes"

echo "[4/4] Merging specs..."
node --experimental-vm-modules "$SCRIPT_DIR/merge-openapi.mjs" "$AUTH_TMP" "$TRANSFER_TMP" 2>/dev/null || \
  node "$SCRIPT_DIR/merge-openapi.mjs" "$AUTH_TMP" "$TRANSFER_TMP"

echo ""
echo "Done. Spec written to: $PROJECT_ROOT/docs/openapi.yaml"
