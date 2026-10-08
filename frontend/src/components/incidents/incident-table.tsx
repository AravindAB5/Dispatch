"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { UserLabel } from "@/components/status/avatar";
import { RecoveredChip, SeverityBadge, StatusBadge } from "@/components/status/badges";
import { RelativeTime } from "@/components/status/relative-time";
import type { IncidentSummary } from "@/lib/api/types";
import { useFlash } from "@/hooks/use-flash";

function IncidentRow({ incident }: { incident: IncidentSummary }) {
  const router = useRouter();
  const ref = useFlash<HTMLTableRowElement>(`incident:${incident.id}`);
  const href = `/incidents/${incident.id}`;
  return (
    <tr
      ref={ref}
      onClick={(e) => {
        if ((e.target as HTMLElement).closest("a")) return;
        router.push(href);
      }}
      className="cursor-pointer transition-colors hover:bg-surface-2"
    >
      <td className="py-3 pl-4 pr-2 align-top">
        <SeverityBadge severity={incident.severity} />
      </td>
      <td className="max-w-0 py-3 pr-4 align-top">
        <Link href={href} className="block truncate text-sm font-medium text-fg hover:text-accent-strong">
          {incident.title}
        </Link>
        <p className="mt-0.5 truncate text-xs text-fg-subtle">
          <span className="font-mono text-fg-muted">{incident.key}</span>
          {incident.service && <> · {incident.service.name}</>}
          {incident.device && <> · {incident.device.identifier}</>}
          {!incident.automated && <> · declared manually</>}
        </p>
      </td>
      <td className="hidden py-3 pr-4 align-top md:table-cell">
        <div className="flex flex-wrap items-center gap-1.5">
          <StatusBadge status={incident.status} />
          {incident.recoveredAt && incident.status !== "RESOLVED" && <RecoveredChip />}
        </div>
      </td>
      <td className="hidden py-3 pr-4 align-top text-sm text-fg-muted lg:table-cell">
        <UserLabel name={incident.assignee?.name} />
      </td>
      <td className="tabular hidden py-3 pr-4 text-right align-top text-sm text-fg-muted xl:table-cell">
        {incident.eventCount}
      </td>
      <td className="hidden py-3 pr-4 align-top text-sm text-fg-muted sm:table-cell">
        <RelativeTime iso={incident.createdAt} />
      </td>
      <td className="hidden py-3 pr-4 align-top text-sm text-fg-subtle xl:table-cell">
        <RelativeTime iso={incident.updatedAt} />
      </td>
    </tr>
  );
}

export function IncidentTable({ incidents }: { incidents: IncidentSummary[] }) {
  return (
    <table className="w-full table-fixed">
      <colgroup>
        <col className="w-[104px]" />
        <col />
        <col className="hidden w-[190px] md:table-column" />
        <col className="hidden w-[170px] lg:table-column" />
        <col className="hidden w-[72px] xl:table-column" />
        <col className="hidden w-[96px] sm:table-column" />
        <col className="hidden w-[96px] xl:table-column" />
      </colgroup>
      <thead>
        <tr className="border-b border-line text-left text-[11px] font-medium uppercase tracking-wide text-fg-subtle">
          <th className="py-2 pl-4 pr-2 font-medium">Severity</th>
          <th className="py-2 pr-4 font-medium">Incident</th>
          <th className="hidden py-2 pr-4 font-medium md:table-cell">Status</th>
          <th className="hidden py-2 pr-4 font-medium lg:table-cell">Assignee</th>
          <th className="hidden py-2 pr-4 text-right font-medium xl:table-cell">Events</th>
          <th className="hidden py-2 pr-4 font-medium sm:table-cell">Opened</th>
          <th className="hidden py-2 pr-4 font-medium xl:table-cell">Updated</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-line">
        {incidents.map((incident) => (
          <IncidentRow key={incident.id} incident={incident} />
        ))}
      </tbody>
    </table>
  );
}
