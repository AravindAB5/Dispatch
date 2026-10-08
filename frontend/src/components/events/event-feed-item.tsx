import Link from "next/link";

import { SeverityBadge } from "@/components/status/badges";
import type { DispatchEvent, IncidentEventRelation } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { eventTypeLabel, sourceLabel } from "@/lib/events";
import { formatClock } from "@/lib/format";

export const RELATION_LABEL: Record<IncidentEventRelation, string> = {
  TRIGGER: "opened",
  OCCURRENCE: "repeat",
  RECOVERY: "recovery",
};

/** One line of the live event feed. */
export function EventFeedItem({ event, fresh }: { event: DispatchEvent; fresh?: boolean }) {
  const link = event.incidents[0];
  return (
    <li className={cn("px-4 py-2.5", fresh && "animate-slide-in")}>
      <div className="flex items-center gap-2 text-xs">
        <time dateTime={event.occurredAt} className="tabular font-mono text-fg-subtle">
          {formatClock(event.occurredAt)}
        </time>
        <span className="truncate font-medium text-fg">{sourceLabel(event)}</span>
        <span className="ml-auto shrink-0">
          {event.severity !== "INFO" && <SeverityBadge severity={event.severity} size="sm" />}
        </span>
      </div>
      <p className="mt-1 truncate text-[13px] text-fg-muted">
        <span className="text-fg">{eventTypeLabel(event.eventType)}</span>
        <span className="text-fg-subtle"> — </span>
        {event.message}
      </p>
      {link && (
        <Link
          href={`/incidents/${link.id}`}
          className="mt-1.5 inline-flex max-w-full items-center gap-1.5 rounded border border-line-strong bg-surface-2 px-1.5 py-0.5 text-[11px] text-fg-muted transition-colors hover:border-accent/50 hover:text-fg"
        >
          <span className="font-mono text-fg">{link.key}</span>
          <span className="text-fg-subtle">{RELATION_LABEL[link.relation]}</span>
          <span className="truncate">{link.title}</span>
        </Link>
      )}
    </li>
  );
}
