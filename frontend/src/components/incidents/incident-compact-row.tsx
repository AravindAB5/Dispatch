"use client";

import Link from "next/link";

import { Avatar } from "@/components/status/avatar";
import { RecoveredChip, SeverityBadge, StatusBadge } from "@/components/status/badges";
import { RelativeTime } from "@/components/status/relative-time";
import type { IncidentSummary } from "@/lib/api/types";
import { useFlash } from "@/hooks/use-flash";

/** Dense incident row for dashboards and side panels. */
export function IncidentCompactRow({ incident }: { incident: IncidentSummary }) {
  const ref = useFlash<HTMLLIElement>(`incident:${incident.id}`);
  const where = incident.device?.identifier ?? incident.service?.name;
  return (
    <li ref={ref}>
      <Link
        href={`/incidents/${incident.id}`}
        className="grid grid-cols-[auto_1fr_auto] items-center gap-x-3 gap-y-1 px-4 py-2.5 transition-colors hover:bg-surface-2"
      >
        <SeverityBadge severity={incident.severity} />
        <div className="min-w-0">
          <p className="truncate text-sm text-fg">{incident.title}</p>
          <p className="truncate text-xs text-fg-subtle">
            <span className="font-mono">{incident.key}</span>
            {where && <> · {where}</>} · opened <RelativeTime iso={incident.createdAt} />
            {incident.eventCount > 1 && <> · {incident.eventCount} events</>}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {incident.recoveredAt && incident.status !== "RESOLVED" && <RecoveredChip className="hidden md:inline-flex" />}
          <StatusBadge status={incident.status} className="hidden sm:inline-flex" />
          {incident.assignee ? (
            <Avatar name={incident.assignee.name} size="sm" />
          ) : (
            <span className="inline-flex size-5 items-center justify-center rounded-full border border-dashed border-line-strong" title="Unassigned" />
          )}
        </div>
      </Link>
    </li>
  );
}
