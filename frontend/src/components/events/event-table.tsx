"use client";

import Link from "next/link";

import { SeverityBadge } from "@/components/status/badges";
import type { DispatchEvent } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { eventTypeLabel, sourceLabel } from "@/lib/events";
import { formatClock, formatShort, isToday } from "@/lib/format";

import { RELATION_LABEL } from "./event-feed-item";

export function EventTable({
  events,
  freshIds,
  onSelect,
}: {
  events: DispatchEvent[];
  freshIds: Set<number>;
  onSelect: (event: DispatchEvent) => void;
}) {
  return (
    <table className="w-full table-fixed text-sm">
      <colgroup>
        <col className="w-[92px] sm:w-[118px]" />
        <col className="w-[88px]" />
        <col className="hidden w-[200px] md:table-column" />
        <col className="hidden w-[170px] lg:table-column" />
        <col />
        <col className="hidden w-[150px] xl:table-column" />
      </colgroup>
      <thead>
        <tr className="border-b border-line text-left text-[11px] uppercase tracking-wide text-fg-subtle">
          <th className="py-2 pl-4 pr-2 font-medium">Time</th>
          <th className="py-2 pr-2 font-medium">Severity</th>
          <th className="hidden py-2 pr-3 font-medium md:table-cell">Type</th>
          <th className="hidden py-2 pr-3 font-medium lg:table-cell">Source</th>
          <th className="py-2 pr-3 font-medium">Message</th>
          <th className="hidden py-2 pr-4 font-medium xl:table-cell">Incident</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-line">
        {events.map((event) => {
          const link = event.incidents[0];
          return (
            <tr
              key={event.id}
              onClick={(e) => {
                if (!(e.target as HTMLElement).closest("a,button")) onSelect(event);
              }}
              className={cn("cursor-pointer transition-colors hover:bg-surface-2", freshIds.has(event.id) && "animate-flash")}
            >
              <td className="tabular py-2.5 pl-4 pr-2 align-top font-mono text-xs text-fg-muted" title={event.occurredAt}>
                {isToday(event.occurredAt) ? formatClock(event.occurredAt) : formatShort(event.occurredAt)}
              </td>
              <td className="py-2.5 pr-2 align-top">
                <SeverityBadge severity={event.severity} size="sm" />
              </td>
              <td className="hidden truncate py-2.5 pr-3 align-top font-mono text-xs text-fg md:table-cell" title={event.eventType}>
                {event.eventType}
              </td>
              <td className="hidden py-2.5 pr-3 align-top lg:table-cell">
                <p className="truncate text-fg">{sourceLabel(event)}</p>
                {event.device && event.service && <p className="truncate text-xs text-fg-subtle">{event.service.name}</p>}
              </td>
              <td className="py-2.5 pr-3 align-top">
                {/* The keyboard-accessible way to open the event inspector (the whole row is clickable too). */}
                <button
                  type="button"
                  onClick={() => onSelect(event)}
                  aria-label={`Inspect ${eventTypeLabel(event.eventType)} from ${sourceLabel(event)}: ${event.message}`}
                  className="block w-full min-w-0 cursor-pointer rounded-sm text-left"
                >
                  <span className="block truncate text-fg-muted">
                    <span className="text-fg md:hidden">{eventTypeLabel(event.eventType)} · </span>
                    {event.message}
                  </span>
                  <span className="block truncate text-xs text-fg-subtle lg:hidden">{sourceLabel(event)}</span>
                </button>
              </td>
              <td className="hidden py-2.5 pr-4 align-top xl:table-cell">
                {link ? (
                  <Link
                    href={`/incidents/${link.id}`}
                    className="inline-flex max-w-full items-center gap-1.5 rounded border border-line-strong bg-surface-2 px-1.5 py-0.5 text-[11px] transition-colors hover:border-accent/50"
                    title={link.title}
                  >
                    <span className="font-mono text-fg">{link.key}</span>
                    <span className="text-fg-subtle">{RELATION_LABEL[link.relation]}</span>
                  </Link>
                ) : (
                  <span className="text-xs text-fg-subtle">—</span>
                )}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
