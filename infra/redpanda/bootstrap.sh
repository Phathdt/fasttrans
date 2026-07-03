#!/usr/bin/env bash
# Explicitly create topics for the demo (1 partition, single-node dev). Idempotent thanks to the -i flag.
set -euo pipefail

BROKER="redpanda:29092"

echo "Creating topics on ${BROKER} ..."
rpk topic create transfer.requested -p 1 -r 1 --brokers "${BROKER}" || true
rpk topic create transfer.result    -p 1 -r 1 --brokers "${BROKER}" || true

echo "Topics:"
rpk topic list --brokers "${BROKER}"
