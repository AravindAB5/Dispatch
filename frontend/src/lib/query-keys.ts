import type { DeviceQuery, EventQuery, IncidentQuery } from "@/lib/api/endpoints";

/**
 * Query keys are hierarchical so realtime messages can invalidate precisely:
 * `["incident", 41]` covers that incident's detail, history, comments and events,
 * while `["incidents"]` covers every incident list.
 */
export const qk = {
  me: ["me"] as const,
  dashboard: {
    all: ["dashboard"] as const,
    summary: ["dashboard", "summary"] as const,
    trend: (hours: number, tz: string) => ["dashboard", "trend", hours, tz] as const,
    volume: (minutes: number) => ["dashboard", "volume", minutes] as const,
  },
  incidents: {
    all: ["incidents"] as const,
    list: (query: IncidentQuery) => ["incidents", query] as const,
  },
  incident: {
    all: (id: number) => ["incident", id] as const,
    detail: (id: number) => ["incident", id, "detail"] as const,
    history: (id: number) => ["incident", id, "history"] as const,
    comments: (id: number) => ["incident", id, "comments"] as const,
    events: (id: number) => ["incident", id, "events"] as const,
  },
  events: {
    all: ["events"] as const,
    stream: (query: EventQuery) => ["events", "stream", query] as const,
    detail: (id: number) => ["events", "detail", id] as const,
    types: ["events", "types"] as const,
  },
  services: {
    all: ["services"] as const,
    detail: (id: number) => ["services", id] as const,
  },
  devices: {
    all: ["devices"] as const,
    list: (query: DeviceQuery) => ["devices", "list", query] as const,
    detail: (id: number) => ["devices", "detail", id] as const,
  },
  rules: ["rules"] as const,
  users: ["users"] as const,
  simulator: ["simulator"] as const,
};
