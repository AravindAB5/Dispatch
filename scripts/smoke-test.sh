#!/usr/bin/env bash
#
# End-to-end smoke test against a running Dispatch stack (docker compose up).
# Walks the core flow through the public API and fails loudly if any step misbehaves:
#
#   login → dashboard → ingest event → incident created → de-duplicated → assign →
#   acknowledge → comment → resolve → history recorded → RBAC and validation checks
#
# Usage: ./scripts/smoke-test.sh   (env: API_URL, INGEST_KEY)

set -euo pipefail

API="${API_URL:-http://localhost:8080}/api/v1"
INGEST_KEY="${INGEST_KEY:-dispatch-dev-ingest-key}"
JSON="Content-Type: application/json"
PASS=0

ok() { PASS=$((PASS + 1)); printf '  \033[32m✓\033[0m %s\n' "$1"; }
fail() { printf '  \033[31m✗ %s\033[0m\n' "$1"; [ -n "${2:-}" ] && printf '    %s\n' "$2"; exit 1; }
field() { grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
number() { grep -o "\"$1\":[0-9]*" | head -1 | cut -d: -f2; }
expect() { echo "$2" | grep -q "$3" && ok "$1" || fail "$1" "$2"; }
status_of() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

echo "Dispatch smoke test → $API"

# --- authentication ---------------------------------------------------------------------
[ "$(status_of "$API/incidents")" = 401 ] && ok "anonymous requests are rejected (401)" || fail "anonymous access not rejected"
BAD=$(curl -s -X POST "$API/auth/login" -H "$JSON" -d '{"email":"john@dispatch.local","password":"wrong"}')
expect "wrong password gives a generic error" "$BAD" '"code":"INVALID_CREDENTIALS"'
LOGIN=$(curl -s -X POST "$API/auth/login" -H "$JSON" -d '{"email":"john@dispatch.local","password":"operator123"}')
TOKEN=$(echo "$LOGIN" | field accessToken)
[ -n "$TOKEN" ] && ok "operator signs in and receives a JWT" || fail "login failed" "$LOGIN"
AUTH="Authorization: Bearer $TOKEN"
ME_ID=$(curl -s "$API/auth/me" -H "$AUTH" | number id)

# --- dashboard --------------------------------------------------------------------------
SUMMARY=$(curl -s "$API/dashboard/summary" -H "$AUTH")
expect "dashboard summary loads" "$SUMMARY" '"activeIncidents"'

# --- ingestion → incident ---------------------------------------------------------------
DEVICE="DEVICE-064"
EVENT="{\"eventType\":\"DEVICE_OFFLINE\",\"severity\":\"CRITICAL\",\"sourceType\":\"DEVICE\",\"sourceId\":\"$DEVICE\",\"message\":\"Smoke test: device stopped responding\"}"
[ "$(status_of -X POST "$API/events" -H "$JSON" -d "$EVENT")" = 401 ] && ok "ingestion requires the API key" || fail "ingestion without key not rejected"
[ "$(status_of -X POST "$API/events" -H "$JSON" -H "$AUTH" -d "$EVENT")" = 403 ] && ok "operators cannot ingest events (403)" || fail "operator ingestion not forbidden"

FIRST=$(curl -s -X POST "$API/events" -H "$JSON" -H "X-API-Key: $INGEST_KEY" -d "$EVENT")
expect "event persisted and rule opened an incident" "$FIRST" '"type":"CREATED"'
INCIDENT=$(echo "$FIRST" | grep -o '"incidentActions":\[{"type":"CREATED","incidentId":[0-9]*' | grep -o '[0-9]*$')
SECOND=$(curl -s -X POST "$API/events" -H "$JSON" -H "X-API-Key: $INGEST_KEY" -d "$EVENT")
expect "repeat event is de-duplicated into INC-$INCIDENT" "$SECOND" "\"type\":\"DEDUPLICATED\",\"incidentId\":$INCIDENT"

DETAIL=$(curl -s "$API/incidents/$INCIDENT" -H "$AUTH")
expect "incident is CRITICAL and OPEN with 2 events" "$DETAIL" '"severity":"CRITICAL","status":"OPEN"'
expect "incident counts both events" "$DETAIL" '"eventCount":2'

# --- operator workflow ------------------------------------------------------------------
R=$(curl -s -X PUT "$API/incidents/$INCIDENT/assignee" -H "$JSON" -H "$AUTH" -d "{\"assigneeId\":$ME_ID}")
expect "incident assigned" "$R" "\"assignee\":{\"id\":$ME_ID"
R=$(curl -s -X PUT "$API/incidents/$INCIDENT/status" -H "$JSON" -H "$AUTH" -d '{"status":"INVESTIGATING"}')
expect "incident acknowledged (INVESTIGATING)" "$R" '"status":"INVESTIGATING"'
R=$(curl -s -X POST "$API/incidents/$INCIDENT/comments" -H "$JSON" -H "$AUTH" -d '{"body":"Smoke test note: power-cycling the device."}')
expect "comment added" "$R" '"body":"Smoke test note: power-cycling the device."'
R=$(curl -s -X PUT "$API/incidents/$INCIDENT/status" -H "$JSON" -H "$AUTH" -d '{"status":"RESOLVED","note":"Smoke test resolution"}')
expect "incident resolved with a note" "$R" '"resolutionNote":"Smoke test resolution"'
R=$(curl -s -X PUT "$API/incidents/$INCIDENT/status" -H "$JSON" -H "$AUTH" -d '{"status":"INVESTIGATING"}')
expect "invalid transition rejected (422)" "$R" '"code":"INVALID_STATUS_TRANSITION"'

HISTORY=$(curl -s "$API/incidents/$INCIDENT/history" -H "$AUTH")
ACTIONS=$(echo "$HISTORY" | grep -o '"action":"[A-Z_]*"' | cut -d'"' -f4 | paste -sd' ' -)
[ "$ACTIONS" = "CREATED ASSIGNED STATUS_CHANGED COMMENT_ADDED STATUS_CHANGED" ] \
  && ok "history recorded: $ACTIONS" || fail "unexpected history" "$ACTIONS"

# --- recovery after resolution ----------------------------------------------------------
RECOVER=$(curl -s -X POST "$API/events" -H "$JSON" -H "X-API-Key: $INGEST_KEY" \
  -d "{\"eventType\":\"DEVICE_RECOVERED\",\"severity\":\"INFO\",\"sourceType\":\"DEVICE\",\"sourceId\":\"$DEVICE\",\"message\":\"Smoke test: device back\"}")
expect "recovery event accepted (nothing left to recover)" "$RECOVER" '"incidentActions":\[\]'

# --- validation & authorization ---------------------------------------------------------
R=$(curl -s -X POST "$API/events" -H "$JSON" -H "X-API-Key: $INGEST_KEY" -d '{"eventType":"bad type","severity":"HIGH"}')
expect "invalid events return field errors (400)" "$R" '"code":"VALIDATION_FAILED"'
[ "$(status_of -X POST "$API/rules" -H "$JSON" -H "$AUTH" -d '{"name":"x","conditionType":"EVENT_TYPE","eventType":"X","incidentSeverity":"LOW"}')" = 403 ] \
  && ok "operators cannot create rules (403)" || fail "operator rule creation not forbidden"

echo "All $PASS checks passed."
