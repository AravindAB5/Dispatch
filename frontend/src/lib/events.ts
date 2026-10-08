import type { EventQuery } from "@/lib/api/endpoints";
import type { DispatchEvent } from "@/lib/api/types";

/** Client-side mirror of the API's event filters, used to decide whether a live event belongs in a view. */
export function matchesEventQuery(event: DispatchEvent, query: EventQuery): boolean {
  if (query.type?.length && !query.type.includes(event.eventType)) return false;
  if (query.severity?.length && !query.severity.includes(event.severity)) return false;
  if (query.serviceId && event.service?.id !== query.serviceId) return false;
  if (query.deviceId && event.device?.id !== query.deviceId) return false;
  if (query.q) {
    const needle = query.q.trim().toLowerCase();
    const haystack = `${event.sourceId} ${event.message} ${event.eventType}`.toLowerCase();
    if (!haystack.includes(needle)) return false;
  }
  return true;
}

function newestFirst(a: DispatchEvent, b: DispatchEvent): number {
  const diff = new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime();
  return diff !== 0 ? diff : b.id - a.id;
}

/**
 * Combines events pushed over the WebSocket with events fetched over REST: keeps only live
 * events matching the current filters, removes duplicates, and orders newest first.
 */
export function mergeLiveEvents(live: DispatchEvent[], fetched: DispatchEvent[], query: EventQuery): DispatchEvent[] {
  const byId = new Map<number, DispatchEvent>();
  for (const event of fetched) byId.set(event.id, event);
  for (const event of live) {
    if (!byId.has(event.id) && matchesEventQuery(event, query)) byId.set(event.id, event);
  }
  return [...byId.values()].sort(newestFirst);
}

/** Short human label for an event type: CPU_USAGE_HIGH → "CPU usage high". */
export function eventTypeLabel(type: string): string {
  const words = type.toLowerCase().split("_");
  const acronyms = new Set(["cpu", "api", "cdn", "tls", "p95"]);
  const text = words.map((w) => (acronyms.has(w) ? w.toUpperCase() : w)).join(" ");
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** Source label: device identifier, service name, or raw source id. */
export function sourceLabel(event: DispatchEvent): string {
  if (event.device) return event.device.identifier;
  if (event.sourceType === "SERVICE" && event.service) return event.service.name;
  return event.sourceId;
}
