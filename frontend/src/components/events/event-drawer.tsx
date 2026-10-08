"use client";

import Link from "next/link";
import type { ReactNode } from "react";

import { SeverityBadge, StatusBadge } from "@/components/status/badges";
import { Dialog } from "@/components/ui/dialog";
import type { DispatchEvent } from "@/lib/api/types";
import { eventTypeLabel } from "@/lib/events";
import { formatFull } from "@/lib/format";

import { RELATION_LABEL } from "./event-feed-item";

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[120px_1fr] gap-3 py-2 text-sm">
      <dt className="text-xs text-fg-subtle">{label}</dt>
      <dd className="min-w-0 text-fg">{children}</dd>
    </div>
  );
}

export function EventDrawer({ event, onClose }: { event: DispatchEvent | null; onClose: () => void }) {
  const latency = event ? new Date(event.receivedAt).getTime() - new Date(event.occurredAt).getTime() : 0;
  return (
    <Dialog
      open={event !== null}
      onClose={onClose}
      variant="drawer"
      title={event ? eventTypeLabel(event.eventType) : "Event"}
      description={event ? `Event #${event.id}` : undefined}
    >
      {event && (
        <div className="space-y-6">
          <p className="text-sm leading-relaxed text-fg">{event.message}</p>
          <dl className="divide-y divide-line">
            <Row label="Type">
              <code className="font-mono text-[13px]">{event.eventType}</code>
            </Row>
            <Row label="Severity">
              <SeverityBadge severity={event.severity} />
            </Row>
            <Row label="Source">
              <span className="text-fg-muted">{event.sourceType.toLowerCase()}</span> ·{" "}
              {event.device ? (
                <Link href={`/devices/${event.device.id}`} className="font-mono hover:text-accent-strong">
                  {event.device.identifier}
                </Link>
              ) : (
                <span className="font-mono">{event.sourceId}</span>
              )}
            </Row>
            <Row label="Service">
              {event.service ? (
                <Link href={`/services/${event.service.id}`} className="hover:text-accent-strong">
                  {event.service.name}
                </Link>
              ) : (
                <span className="text-fg-subtle">—</span>
              )}
            </Row>
            <Row label="Occurred">{formatFull(event.occurredAt)}</Row>
            <Row label="Received">
              {formatFull(event.receivedAt)}
              <span className="text-xs text-fg-subtle"> · {latency >= 0 ? `+${latency} ms` : "clock skew"}</span>
            </Row>
          </dl>

          <section>
            <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-fg-subtle">Metadata</h3>
            <pre className="overflow-x-auto rounded-md border border-line bg-canvas p-3 font-mono text-xs leading-relaxed text-fg-muted">
              {JSON.stringify(event.metadata, null, 2)}
            </pre>
          </section>

          <section>
            <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-fg-subtle">Incidents</h3>
            {event.incidents.length === 0 ? (
              <p className="text-sm text-fg-subtle">This event did not match any alert rule.</p>
            ) : (
              <ul className="space-y-2">
                {event.incidents.map((link) => (
                  <li key={link.id}>
                    <Link
                      href={`/incidents/${link.id}`}
                      className="flex items-center gap-3 rounded-md border border-line-strong bg-surface-2 px-3 py-2 transition-colors hover:border-accent/50"
                    >
                      <SeverityBadge severity={link.severity} size="sm" />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm text-fg">{link.title}</span>
                        <span className="text-xs text-fg-subtle">
                          <span className="font-mono">{link.key}</span> · this event was the {RELATION_LABEL[link.relation]}
                        </span>
                      </span>
                      <StatusBadge status={link.status} />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
    </Dialog>
  );
}
