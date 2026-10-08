# API reference

Base URL: `http://localhost:8080/api/v1`. JSON in and out. Timestamps are ISO-8601 UTC.

- [Authentication](#authentication)
- [Errors](#errors)
- [Events](#events)
- [Incidents](#incidents)
- [Alert rules](#alert-rules)
- [Services & devices](#services--devices)
- [Users](#users)
- [Dashboard](#dashboard)
- [Simulator](#simulator)
- [WebSocket](#websocket)

---

## Authentication

| Caller | How | Role |
|---|---|---|
| People | `Authorization: Bearer <accessToken>` from `POST /auth/login` | `ADMIN` or `OPERATOR` |
| Event producers | `X-API-Key: <DISPATCH_INGEST_API_KEY>` | `INGEST` (may only `POST /events`) |

### `POST /auth/login`

```json
{ "email": "operator@dispatch.local", "password": "operator123" }
```

`200`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-09T06:00:00Z",
  "user": { "id": 2, "name": "Aravind B", "email": "operator@dispatch.local", "role": "OPERATOR" }
}
```

`401 INVALID_CREDENTIALS` (same response for unknown email and wrong password) · `403 ACCOUNT_DISABLED`.

### `GET /auth/me`

The signed-in user. `401 SESSION_INVALID` if the account has since been deactivated.

## Errors

RFC 9457 problem details with `code`, `requestId`, `timestamp`, and `errors` for field validation.
See the [status/code table in the architecture doc](architecture.md#error-handling).

```json
{
  "type": "https://dispatch.local/problems/invalid-status-transition",
  "title": "Unprocessable Content",
  "status": 422,
  "detail": "Cannot change status from RESOLVED to INVESTIGATING",
  "code": "INVALID_STATUS_TRANSITION",
  "requestId": "332bff88-13f0-4164-be9a-fc505f64c6a9",
  "timestamp": "2026-10-08T17:59:56.979Z"
}
```

## Events

### `POST /events`: ingest an event

Auth: `X-API-Key` or an `ADMIN` bearer token.

| Field | Type | Notes |
|---|---|---|
| `eventType` | string | required, `UPPER_SNAKE_CASE`, ≤ 64 |
| `severity` | `INFO` `LOW` `MEDIUM` `HIGH` `CRITICAL` | required |
| `sourceType` | `DEVICE` `SERVICE` `EXTERNAL` | required |
| `sourceId` | string | required. For `DEVICE`, a registered device identifier (case-insensitive) |
| `serviceId` | number | required for `SERVICE`; optional otherwise (must match the device's service) |
| `message` | string | required, ≤ 1000 |
| `metadata` | object | optional, ≤ 50 keys / 16 KB; numeric values are usable by metric rules |
| `timestamp` | ISO-8601 | optional, defaults to receive time; ≤ 5 min in the future |

```bash
curl -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" -H "X-API-Key: dispatch-dev-ingest-key" \
  -d '{"eventType":"DEVICE_OFFLINE","severity":"CRITICAL","sourceType":"DEVICE",
       "sourceId":"DEVICE-108","message":"Device stopped responding","metadata":{"missedHeartbeats":3}}'
```

`201` with `Location: /api/v1/events/{id}`:

```json
{
  "event": {
    "id": 1303, "eventType": "DEVICE_OFFLINE", "severity": "CRITICAL",
    "sourceType": "DEVICE", "sourceId": "DEVICE-108",
    "service": { "id": 3, "name": "Device Gateway", "status": "DEGRADED" },
    "device": { "id": 14, "identifier": "DEVICE-108", "name": "Gateway node 03", "status": "OFFLINE" },
    "message": "Device stopped responding", "metadata": { "missedHeartbeats": 3 },
    "occurredAt": "2026-10-08T18:41:18.120Z", "receivedAt": "2026-10-08T18:41:18.120Z",
    "incidents": [ { "id": 81, "key": "INC-81", "title": "DEVICE-108 is offline", "severity": "CRITICAL", "status": "OPEN", "relation": "TRIGGER" } ]
  },
  "incidentActions": [
    { "type": "CREATED", "incidentId": 81, "key": "INC-81", "title": "DEVICE-108 is offline",
      "severity": "CRITICAL", "status": "OPEN", "ruleId": 1, "ruleName": "Device offline", "serviceId": 3, "deviceId": 14 }
  ]
}
```

`incidentActions[].type`: `CREATED` · `DEDUPLICATED` · `RECURRED` · `RECOVERY_DETECTED` · `AUTO_RESOLVED`.
Errors: `400` validation (unknown device or service, future timestamp, bad enum) · `401` bad key · `403` operator token.

### `GET /events`: keyset-paginated stream

| Param | Notes |
|---|---|
| `type` | repeatable, exact event type |
| `severity` | repeatable |
| `serviceId`, `deviceId`, `sourceType` | |
| `q` | substring of source id, message or type |
| `from`, `to` | ISO-8601 on `occurredAt` |
| `limit` | 1–100, default 50 |
| `cursor` | `nextCursor` from the previous page |

```json
{ "items": [ /* EventResponse, newest first */ ], "nextCursor": "MjAyNi0xMC0wOFQx…", "hasMore": true }
```

### `GET /events/{id}` · `GET /events/types`

A single event with its linked incidents; the distinct event types seen so far.

## Incidents

### `GET /incidents`

| Param | Notes |
|---|---|
| `status` | repeatable: `OPEN`, `INVESTIGATING`, `RESOLVED` |
| `severity` | repeatable |
| `serviceId`, `deviceId`, `assigneeId` | |
| `unassigned` | `true` for incidents without an owner |
| `q` | title, device identifier, `INC-123` or `123` |
| `createdFrom`, `createdTo` | ISO-8601 |
| `sort` | `newest` (default), `oldest`, `updated`, `severity` |
| `page`, `size` | zero-based page, size ≤ 100 (default 20) |

```json
{
  "content": [
    {
      "id": 41, "key": "INC-41", "title": "DEVICE-108 is offline",
      "severity": "CRITICAL", "status": "INVESTIGATING",
      "service": { "id": 3, "name": "Device Gateway", "status": "DEGRADED" },
      "device": { "id": 14, "identifier": "DEVICE-108", "name": "Gateway node 03", "status": "OFFLINE" },
      "assignee": { "id": 2, "name": "Aravind B", "email": "operator@dispatch.local" },
      "automated": true, "eventCount": 6,
      "lastEventAt": "…", "acknowledgedAt": "…", "recoveredAt": null, "resolvedAt": null,
      "createdAt": "…", "updatedAt": "…"
    }
  ],
  "page": 0, "size": 20, "totalElements": 52, "totalPages": 3
}
```

### `GET /incidents/{id}`

The summary fields plus `description`, `rule` (`{id, name, condition, recoveryEventType, autoResolve}`),
`createdBy`, `fingerprint`, `sourceEventId` and `resolutionNote`.

### `GET /incidents/{id}/history`

Chronological audit entries:

```json
[
  { "id": 1, "action": "CREATED", "fromValue": null, "toValue": "CRITICAL",
    "message": "Incident created by rule \"Device offline\" from a DEVICE_OFFLINE event", "actor": null, "createdAt": "…" },
  { "id": 2, "action": "ASSIGNED", "fromValue": null, "toValue": "Aravind B",
    "message": "Assigned to Aravind B", "actor": { "id": 2, "name": "Aravind B", "email": "…" }, "createdAt": "…" }
]
```

`action` ∈ `CREATED`, `STATUS_CHANGED`, `SEVERITY_CHANGED`, `ASSIGNED`, `UNASSIGNED`, `COMMENT_ADDED`,
`RECOVERY_DETECTED`, `RECURRED`, `AUTO_RESOLVED`, `REOPENED`. A `null` actor means Dispatch made the change.

### `GET /incidents/{id}/comments` · `GET /incidents/{id}/events?limit=50`

Comments oldest first. Related events, newest first, with totals:
`{ "total": 6, "triggers": 1, "occurrences": 5, "recoveries": 0, "items": [ { "relation": "OCCURRENCE", "linkedAt": "…", "event": { … } } ] }`.

### Commands (ADMIN or OPERATOR)

| Request | Body | Notes |
|---|---|---|
| `POST /incidents` | `{title, severity, description?, serviceId?, deviceId?}` | Declare manually → `201` |
| `PUT /incidents/{id}/assignee` | `{assigneeId: number \| null}` | `null` unassigns; deactivated users rejected (`422 ASSIGNEE_INACTIVE`); no-op if unchanged |
| `PUT /incidents/{id}/status` | `{status, note?}` | Transitions per the [lifecycle](architecture.md#incident-lifecycle); `note` becomes the resolution note when resolving |
| `PUT /incidents/{id}/severity` | `{severity}` | |
| `POST /incidents/{id}/comments` | `{body}` | ≤ 5000 chars → `201` |

Commands return the updated incident (`IncidentDetail`). Errors: `422 INVALID_STATUS_TRANSITION`,
`422 STATUS_UNCHANGED`, `422 INCIDENT_RESOLVED`, `409 ACTIVE_DUPLICATE_EXISTS` (reopen blocked),
`409 CONCURRENT_MODIFICATION`.

## Alert rules

`GET /rules` and `GET /rules/{id}` for everyone; writes need ADMIN.

```json
{
  "name": "High CPU usage",
  "description": "Devices report CPU above 80%; Dispatch only pages above 90%.",
  "conditionType": "METRIC_THRESHOLD",
  "eventType": "CPU_USAGE_HIGH",
  "metricKey": "cpu",
  "operator": "GT",
  "threshold": 90,
  "serviceId": null,
  "incidentSeverity": "HIGH",
  "titleTemplate": "High CPU usage on {source} ({value}%)",
  "recoveryEventType": "CPU_USAGE_NORMAL",
  "autoResolve": true,
  "enabled": true
}
```

| `conditionType` | Required fields |
|---|---|
| `EVENT_TYPE` | `eventType` |
| `METRIC_THRESHOLD` | `eventType`, `metricKey`, `operator` (`GT` `GTE` `LT` `LTE`), `threshold` |
| `SEVERITY_AT_LEAST` | `minEventSeverity` (`eventType` optional) |

Title placeholders: `{source}` `{device}` `{service}` `{rule}` `{eventType}` `{metric}` `{value}`.

| Request | Notes |
|---|---|
| `POST /rules` | `201` · `409 RULE_NAME_TAKEN` · `400` per-field errors for missing condition fields or unknown placeholders |
| `PUT /rules/{id}` | full replacement |
| `PATCH /rules/{id}` | `{ "enabled": false }` |
| `DELETE /rules/{id}` | `204`; incidents it opened are kept (rule reference cleared) |

Responses add `condition` (e.g. `"cpu > 90 on CPU_USAGE_HIGH"`), `activeIncidents` and `totalIncidents`.

## Services & devices

| Request | Notes |
|---|---|
| `GET /services` | All services with derived `status`, `statusChangedAt`, `devices {total, online, degraded, offline}` and `activeIncidents {total, critical, high, medium, low}` |
| `GET /services/{id}` | |
| `POST /services`, `PUT /services/{id}` | ADMIN: `{name, environment: PRODUCTION\|STAGING\|DEVELOPMENT, ownerTeam?, description?}` |
| `GET /devices?serviceId=&status=&q=&page=&size=` | Paginated; `status` repeatable (`ONLINE`, `DEGRADED`, `OFFLINE`) |
| `GET /devices/{id}` | |
| `POST /devices`, `PUT /devices/{id}` | ADMIN: `{identifier, name, deviceType, serviceId, location?}`; the identifier is immutable |

## Users

| Request | Notes |
|---|---|
| `GET /users` | Team with role, active flag and number of active assigned incidents |
| `POST /users` | ADMIN: `{name, email, password (8–72), role}` · `409 EMAIL_TAKEN` |
| `PATCH /users/{id}` | ADMIN: `{name?, role?, active?}` · `422 SELF_LOCKOUT` (demoting/deactivating yourself) · `422 LAST_ADMIN` |

## Dashboard

| Request | Response |
|---|---|
| `GET /dashboard/summary` | active incidents by severity/status, unassigned, last-24h created/resolved, MTTA/MTTR (7 days, seconds), events last hour/24h, service and device health counts |
| `GET /dashboard/incident-trend?hours=24&tz=Asia/Kolkata` | `[{bucketStart, critical, high, medium, low, resolved}]`; hourly buckets aligned to `tz` (1–168 hours) |
| `GET /dashboard/event-volume?minutes=60` | `[{bucketStart, total, problems}]` per minute (5–360 minutes) |

## Simulator

Present only when `DISPATCH_SIMULATOR_ENABLED=true` (otherwise `404`).

| Request | Notes |
|---|---|
| `GET /simulator` | `{running, startedAt, intervalMs, eventsGenerated, maxConcurrentProblems, activeProblems[]}` |
| `POST /simulator/start`, `POST /simulator/stop` | ADMIN |

## WebSocket

- Endpoint: `ws://localhost:8080/ws` (STOMP 1.2 over WebSocket, no SockJS).
- `CONNECT` frame header: `Authorization: Bearer <accessToken>`. An invalid or missing token gets an
  `ERROR` frame and the connection is closed.
- Subscribe to any of `/topic/events`, `/topic/incidents`, `/topic/services`, `/topic/devices`.
  Other destinations and `SEND` frames are rejected.
- Heart-beats: 10 s in each direction.

```js
import { Client } from "@stomp/stompjs";

const client = new Client({
  brokerURL: "ws://localhost:8080/ws",
  connectHeaders: { Authorization: `Bearer ${token}` },
  onConnect: () => {
    client.subscribe("/topic/incidents", (frame) => {
      const { type, data } = JSON.parse(frame.body); // "incident.created", IncidentSummary
      console.log(type, data.key, data.title);
    });
  },
});
client.activate();
```

Messages are sent only after the database transaction that produced them commits.
