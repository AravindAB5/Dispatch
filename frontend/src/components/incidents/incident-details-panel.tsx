"use client";

import Link from "next/link";
import type { ReactNode } from "react";

import { UserLabel } from "@/components/status/avatar";
import { DeviceStatusBadge, HealthBadge, SeverityBadge, StatusBadge } from "@/components/status/badges";
import { RelativeTime } from "@/components/status/relative-time";
import { Panel } from "@/components/ui/panel";
import type { IncidentDetail } from "@/lib/api/types";
import { durationBetween, formatDuration, formatShort } from "@/lib/format";
import { useNow } from "@/stores/clock-store";

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[112px_1fr] items-start gap-3 py-2">
      <dt className="pt-px text-xs text-fg-subtle">{label}</dt>
      <dd className="min-w-0 text-sm text-fg">{children}</dd>
    </div>
  );
}

function Timestamp({ iso, suffix }: { iso: string | null; suffix?: ReactNode }) {
  if (!iso) return <span className="text-fg-subtle">—</span>;
  return (
    <span className="block">
      <span className="tabular">{formatShort(iso)}</span>
      <span className="text-xs text-fg-subtle">
        {" "}
        · <RelativeTime iso={iso} />
        {suffix}
      </span>
    </span>
  );
}

export function IncidentDetailsPanel({ incident }: { incident: IncidentDetail }) {
  const now = useNow();
  const tta = durationBetween(incident.createdAt, incident.acknowledgedAt);
  const age = durationBetween(incident.createdAt, incident.resolvedAt, now);

  return (
    <Panel title="Details" bodyClassName="px-4 py-1.5">
      <dl className="divide-y divide-line">
        <Row label="Status">
          <StatusBadge status={incident.status} />
        </Row>
        <Row label="Severity">
          <SeverityBadge severity={incident.severity} />
        </Row>
        <Row label="Assignee">
          <UserLabel name={incident.assignee?.name} />
        </Row>
        <Row label="Service">
          {incident.service ? (
            <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <Link href={`/services/${incident.service.id}`} className="hover:text-accent-strong">
                {incident.service.name}
              </Link>
              <HealthBadge status={incident.service.status} />
            </span>
          ) : (
            <span className="text-fg-subtle">—</span>
          )}
        </Row>
        <Row label="Device">
          {incident.device ? (
            <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <Link href={`/devices/${incident.device.id}`} className="font-mono text-[13px] hover:text-accent-strong">
                {incident.device.identifier}
              </Link>
              <DeviceStatusBadge status={incident.device.status} />
            </span>
          ) : (
            <span className="text-fg-subtle">—</span>
          )}
        </Row>
        <Row label="Source">
          {incident.rule ? (
            <span className="block">
              <Link href="/rules" className="hover:text-accent-strong">
                Rule · {incident.rule.name}
              </Link>
              <span className="mt-0.5 block font-mono text-[11px] text-fg-subtle">{incident.rule.condition}</span>
            </span>
          ) : incident.createdBy ? (
            <>Declared by {incident.createdBy.name}</>
          ) : (
            <span className="text-fg-subtle">Rule deleted</span>
          )}
        </Row>
        <Row label="Opened">
          <Timestamp iso={incident.createdAt} />
        </Row>
        <Row label="Acknowledged">
          <Timestamp iso={incident.acknowledgedAt} suffix={tta !== null && incident.acknowledgedAt ? ` · TTA ${formatDuration(tta)}` : null} />
        </Row>
        <Row label={incident.resolvedAt ? "Resolved" : "Open for"}>
          {incident.resolvedAt ? (
            <Timestamp iso={incident.resolvedAt} suffix={` · took ${formatDuration(age)}`} />
          ) : (
            <span className="tabular">{formatDuration(age)}</span>
          )}
        </Row>
        <Row label="Events">
          <span className="tabular">{incident.eventCount}</span>
          {incident.lastEventAt && (
            <span className="text-xs text-fg-subtle">
              {" "}
              · last <RelativeTime iso={incident.lastEventAt} />
            </span>
          )}
        </Row>
        {incident.fingerprint && (
          <Row label="Fingerprint">
            <code className="break-all font-mono text-[11px] text-fg-muted" title="Events with the same fingerprint are de-duplicated into this incident">
              {incident.fingerprint}
            </code>
          </Row>
        )}
      </dl>
    </Panel>
  );
}
