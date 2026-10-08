import type { Comment, DispatchEvent, HistoryEntry, IncidentDetail, IncidentSummary, User } from "@/lib/api/types";

export function makeEvent(overrides: Partial<DispatchEvent> = {}): DispatchEvent {
  return {
    id: 1,
    eventType: "DEVICE_OFFLINE",
    severity: "CRITICAL",
    sourceType: "DEVICE",
    sourceId: "DEVICE-108",
    service: { id: 3, name: "Device Gateway", status: "DEGRADED" },
    device: { id: 8, identifier: "DEVICE-108", name: "Gateway node 03", status: "OFFLINE" },
    message: "Device stopped responding",
    metadata: { missedHeartbeats: 3 },
    occurredAt: "2026-10-08T09:41:18Z",
    receivedAt: "2026-10-08T09:41:18.120Z",
    incidents: [],
    ...overrides,
  };
}

export function makeIncident(overrides: Partial<IncidentDetail> = {}): IncidentDetail {
  return {
    id: 41,
    key: "INC-41",
    title: "DEVICE-108 is offline",
    severity: "CRITICAL",
    status: "OPEN",
    service: { id: 3, name: "Device Gateway", status: "DEGRADED" },
    device: { id: 8, identifier: "DEVICE-108", name: "Gateway node 03", status: "OFFLINE" },
    assignee: null,
    automated: true,
    eventCount: 4,
    lastEventAt: "2026-10-08T09:43:00Z",
    acknowledgedAt: null,
    recoveredAt: null,
    resolvedAt: null,
    createdAt: "2026-10-08T09:41:18Z",
    updatedAt: "2026-10-08T09:43:00Z",
    description: "Device stopped responding",
    createdBy: null,
    rule: { id: 1, name: "Device offline", condition: "DEVICE_OFFLINE received", recoveryEventType: "DEVICE_RECOVERED", autoResolve: true },
    fingerprint: "rule:1|device:8",
    sourceEventId: 1,
    resolutionNote: null,
    ...overrides,
  };
}

export function makeSummary(overrides: Partial<IncidentSummary> = {}): IncidentSummary {
  const d = makeIncident();
  return {
    id: d.id, key: d.key, title: d.title, severity: d.severity, status: d.status, service: d.service, device: d.device,
    assignee: d.assignee, automated: d.automated, eventCount: d.eventCount, lastEventAt: d.lastEventAt,
    acknowledgedAt: d.acknowledgedAt, recoveredAt: d.recoveredAt, resolvedAt: d.resolvedAt, createdAt: d.createdAt,
    updatedAt: d.updatedAt, ...overrides,
  };
}

export function makeHistory(overrides: Partial<HistoryEntry> = {}): HistoryEntry {
  return {
    id: 1,
    action: "CREATED",
    fromValue: null,
    toValue: "CRITICAL",
    message: 'Incident created by rule "Device offline" from a DEVICE_OFFLINE event',
    actor: null,
    createdAt: "2026-10-08T09:41:18Z",
    ...overrides,
  };
}

export function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 1,
    incidentId: 41,
    body: "Checked switch port 14.",
    author: { id: 2, name: "Aravind B", email: "operator@dispatch.local" },
    createdAt: "2026-10-08T09:46:31Z",
    ...overrides,
  };
}

export function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 2,
    name: "Aravind B",
    email: "operator@dispatch.local",
    role: "OPERATOR",
    active: true,
    activeIncidents: 1,
    createdAt: "2026-07-10T00:00:00Z",
    ...overrides,
  };
}

/** A minimal fetch Response for mocking the API. */
export function jsonResponse(status: number, body: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}
