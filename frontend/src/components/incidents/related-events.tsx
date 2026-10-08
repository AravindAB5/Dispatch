"use client";

import type { IncidentEventRelation, RelatedEvents } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { eventTypeLabel } from "@/lib/events";
import { formatClock, formatShort, isToday } from "@/lib/format";

const RELATION: Record<IncidentEventRelation, { label: string; className: string }> = {
  TRIGGER: { label: "Trigger", className: "border-sev-critical/40 text-sev-critical" },
  OCCURRENCE: { label: "Repeat", className: "border-line-strong text-fg-muted" },
  RECOVERY: { label: "Recovery", className: "border-ok/40 text-ok" },
};

function MetadataPreview({ metadata }: { metadata: Record<string, unknown> }) {
  const entries = Object.entries(metadata).slice(0, 4);
  if (!entries.length) return null;
  return (
    <p className="mt-1 truncate font-mono text-[11px] text-fg-subtle">
      {entries.map(([key, value]) => `${key}=${typeof value === "object" ? JSON.stringify(value) : String(value)}`).join("  ")}
    </p>
  );
}

export function RelatedEventList({ data }: { data: RelatedEvents }) {
  return (
    <div>
      <div className="flex flex-wrap gap-2 border-b border-line px-4 py-2.5 text-xs text-fg-muted">
        <span>
          <span className="tabular font-semibold text-fg">{data.total}</span> events
        </span>
        <span className="text-fg-subtle">·</span>
        <span>{data.triggers} trigger</span>
        <span className="text-fg-subtle">·</span>
        <span title="Repeats of the same problem, absorbed by de-duplication">{data.occurrences} repeats</span>
        <span className="text-fg-subtle">·</span>
        <span>{data.recoveries} recovery</span>
      </div>
      <ul className="max-h-[420px] divide-y divide-line overflow-y-auto">
        {data.items.map(({ relation, event }) => (
          <li key={`${relation}-${event.id}`} className="flex gap-3 px-4 py-2.5">
            <span
              className={cn(
                "mt-0.5 inline-flex h-5 w-[68px] shrink-0 items-center justify-center rounded border text-[10px] font-medium uppercase tracking-wide",
                RELATION[relation].className,
              )}
            >
              {RELATION[relation].label}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm">
                <span className="text-fg">{eventTypeLabel(event.eventType)}</span>
                <span className="text-fg-subtle"> — </span>
                <span className="text-fg-muted">{event.message}</span>
              </p>
              <MetadataPreview metadata={event.metadata} />
            </div>
            <time dateTime={event.occurredAt} className="tabular shrink-0 font-mono text-xs text-fg-subtle" title={event.occurredAt}>
              {isToday(event.occurredAt) ? formatClock(event.occurredAt) : formatShort(event.occurredAt)}
            </time>
          </li>
        ))}
      </ul>
      {data.total > data.items.length && (
        <p className="border-t border-line px-4 py-2 text-xs text-fg-subtle">
          Showing the latest {data.items.length} of {data.total} events.
        </p>
      )}
    </div>
  );
}
