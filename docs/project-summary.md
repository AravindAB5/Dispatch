# Dispatch: project summary (interview prep)

## What it is

A real-time incident management platform. Devices and services send events; alert rules turn real
problems into incidents; operators acknowledge, assign, comment on and resolve them in a live console.
Repeated alerts are de-duplicated, self-healing problems resolve themselves, and service health is
derived from incidents. A full-stack modular monolith: Spring Boot API, PostgreSQL, Next.js console,
WebSockets, Docker Compose.

## Architecture in one breath

Producers `POST /api/v1/events` with an API key. `EventIngestionService` validates and stores the event,
`RuleEvaluator` (one strategy per rule type) finds matching and recovering rules, and
`IncidentAutomationService` creates, de-duplicates or recovers incidents under a PostgreSQL advisory lock.
`HealthService` re-derives device and service status. Everything happens in one transaction, and domain
events are broadcast over STOMP **after commit**. The Next.js console uses TanStack Query for server
state; socket messages invalidate exactly the affected queries, and Zustand holds the live buffer,
connection state and toasts.

## Decisions worth explaining

1. **Modular monolith, synchronous pipeline.** Atomic event + incident, simple ops; the split point (ingestion → queue) is known.
2. **De-dup = fingerprint (rule + source) + advisory lock + partial unique index.** Correct under concurrency (tested with 8 parallel duplicates) without serializable isolation.
3. **Recovery respects humans.** Auto-resolve only `OPEN` incidents; `INVESTIGATING` or confirmation-required incidents are flagged *Recovered*. Recurrence clears the flag.
4. **Derived health.** Computed from connectivity + impacting incidents, so it can never disagree with the incident list.
5. **After-commit broadcast with DTO snapshots.** No phantom notifications for rolled-back work, no reads after commit.
6. **Socket messages are signals, not state.** Targeted, coalesced cache invalidation keeps REST as the source of truth; refetch everything after a reconnect.
7. **Spring Security resource server for JWT** (HS256, issuer check, roles claim) plus a constant-time API-key filter for machines (`ROLE_INGEST`).
8. **Keyset pagination for events, offset for incidents.** Append-only and huge vs. small with useful totals.
9. **Flyway + `ddl-auto=validate`, invariants as constraints.** Partial unique index, `resolved_at` iff `RESOLVED`, per-rule-type CHECKs; generated `severity_rank` for sorting.
10. **RFC 9457 problem details with stable codes and request ids**, mapped to specific UI messages and field errors.

## Run / test / demo

```bash
docker compose up --build                       # http://localhost:3000 · admin@dispatch.local / admin123
./scripts/demo.sh DEVICE-042                    # offline → de-duplicated repeats → auto-resolved
./scripts/smoke-test.sh                         # 19 end-to-end API checks
cd backend && ./mvnw test                       # 97 tests (unit + Testcontainers incl. WebSocket)
cd frontend && npm test                         # 67 tests (Vitest + Testing Library)
```

## Résumé bullets

- Built **Dispatch**, a real-time incident management platform (Java 25, Spring Boot 4, PostgreSQL, Next.js 16, TypeScript, WebSockets, Docker) that ingests device and service events, evaluates alert rules and manages the incident lifecycle end to end.
- Designed concurrency-safe alert **de-duplication** using rule+source fingerprints, PostgreSQL transaction-scoped advisory locks and a partial unique index, verified with parallel integration tests on Testcontainers.
- Implemented **real-time updates** via STOMP over WebSocket with JWT-authenticated connections and after-commit event broadcasting, keeping the React console in sync through targeted TanStack Query invalidation instead of polling.
- Secured the API with **Spring Security** (JWT resource server, BCrypt, role-based access, API keys for producers) and consistent RFC 9457 error responses.
- Wrote **160+ automated tests** across unit, integration, WebSocket and UI layers, and shipped a one-command Docker Compose environment with seeded data and an event simulator.

## Interview talking points

- *How do you avoid duplicate incidents when the same alert arrives concurrently?* Lock on the fingerprint inside the transaction, re-read after acquiring it, plus a DB constraint as the last line of defence. Why not `SELECT … FOR UPDATE`? There is no row to lock yet when the first event arrives.
- *Why broadcast after commit?* Otherwise clients can see incidents that are then rolled back, or refetch before the data is visible.
- *How does the UI stay consistent with missed messages?* Messages only trigger invalidation; after a reconnect, every query is refetched.
- *Auto-resolution trade-off:* closing an incident someone is actively working on would hide their work, so flag instead.
- *Why keyset pagination for events?* Constant-time pages on an ever-growing table, and stable results while new rows arrive.
- *Scaling next steps:* broker relay for multiple API instances, queue-based ingestion, idempotency keys, event partitioning and retention.
- *Testing strategy:* pure units for the rule engine and aggregate; Testcontainers integration for SQL, locks and constraints; a real STOMP client for WebSockets; component tests for UI behaviour.

## Known limitations

In-memory STOMP broker (single instance) · JS-readable token cookie and no refresh tokens · single shared ingest key and no rate limiting · synchronous rule evaluation · no event retention · no missed-heartbeat detection · dark theme only.

## Next steps

Escalation policies and on-call · Slack/email/webhook notifications · sustained/compound rule conditions and maintenance windows · heartbeat-timeout detection · configuration audit log · Prometheus/Alertmanager and cloud monitoring integrations · horizontal scaling.
