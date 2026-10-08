#!/usr/bin/env bash
#
# Walks one problem through Dispatch using the public ingestion API:
#   1. a device goes offline        -> incident CREATED
#   2. it keeps reporting offline   -> DEDUPLICATED into the same incident
#   3. it comes back                -> AUTO_RESOLVED (nobody was investigating)
#
# Watch the dashboard at http://localhost:3000 while it runs.
#
# Usage: ./scripts/demo.sh [DEVICE_IDENTIFIER]
# Env:   API_URL (default http://localhost:8080), INGEST_KEY (default dispatch-dev-ingest-key)

set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
INGEST_KEY="${INGEST_KEY:-dispatch-dev-ingest-key}"
DEVICE="${1:-DEVICE-042}"

send() {
  local type="$1" severity="$2" message="$3" metadata="${4:-{\}}"
  curl -sS -X POST "$API_URL/api/v1/events" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $INGEST_KEY" \
    -d "{\"eventType\":\"$type\",\"severity\":\"$severity\",\"sourceType\":\"DEVICE\",\"sourceId\":\"$DEVICE\",\"message\":\"$message\",\"metadata\":$metadata}"
}

summarise() {
  # Print what alert processing did, without requiring jq.
  grep -o '"incidentActions":\[[^]]*\]' | sed -E 's/"incidentActions":\[//; s/\]$//' \
    | grep -oE '"type":"[A-Z_]+"|"key":"INC-[0-9]+"|"title":"[^"]*"|"status":"[A-Z]+"' \
    | paste -sd' ' - | sed 's/^$/(no rule matched)/'
}

echo "1) $DEVICE goes offline"
send DEVICE_OFFLINE CRITICAL "Device stopped responding" '{"missedHeartbeats":3}' | summarise
sleep 2

for i in 1 2 3; do
  echo "2.$i) $DEVICE still offline"
  send DEVICE_OFFLINE CRITICAL "Device still unreachable" "{\"missedHeartbeats\":$((3 + i * 3))}" | summarise
  sleep 2
done

echo "3) $DEVICE reconnects"
send DEVICE_RECOVERED INFO "Device reconnected" '{"downtimeSeconds":8}' | summarise
