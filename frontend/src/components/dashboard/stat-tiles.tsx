"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowUpRight, Timer, UserX } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

import { Skeleton } from "@/components/ui/states";
import { dashboardApi } from "@/lib/api/endpoints";
import type { DashboardSummary, IncidentSeverity } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { SEVERITY_LABEL } from "@/lib/domain";
import { compactNumber, formatDuration } from "@/lib/format";
import { qk } from "@/lib/query-keys";

import { VolumeSparkline } from "./sparkline";

function Tile({ label, children, href, className }: { label: ReactNode; children: ReactNode; href?: string; className?: string }) {
  const content = (
    <>
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-medium text-fg-muted">{label}</p>
        {href && <ArrowUpRight className="size-3.5 text-fg-subtle transition-colors group-hover:text-fg" aria-hidden />}
      </div>
      <div className="mt-3">{children}</div>
    </>
  );
  const classes = cn("group block rounded-lg border border-line bg-surface p-4", href && "transition-colors hover:border-line-strong", className);
  return href ? (
    <Link href={href} className={classes}>
      {content}
    </Link>
  ) : (
    <div className={classes}>{content}</div>
  );
}

const severityDot: Record<IncidentSeverity, string> = {
  CRITICAL: "bg-sev-critical",
  HIGH: "bg-sev-high",
  MEDIUM: "bg-sev-medium",
  LOW: "bg-sev-low",
};

function SeverityCount({ severity, count }: { severity: IncidentSeverity; count: number }) {
  return (
    <Link
      href={`/incidents?severity=${severity}`}
      className="flex items-center gap-2 rounded px-1 py-0.5 text-xs transition-colors hover:bg-surface-3"
    >
      <span className={cn("size-2 rounded-[2px]", severityDot[severity])} aria-hidden />
      <span className={cn("tabular font-semibold", count ? "text-fg" : "text-fg-subtle")}>{count}</span>
      <span className="text-fg-muted">{SEVERITY_LABEL[severity]}</span>
    </Link>
  );
}

export function StatTiles({ summary }: { summary: DashboardSummary | undefined }) {
  const volume = useQuery({ queryKey: qk.dashboard.volume(60), queryFn: () => dashboardApi.volume(60) });

  if (!summary) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="h-[150px] rounded-lg border border-line bg-surface p-4">
            <Skeleton className="h-3 w-24" />
            <Skeleton className="mt-4 h-9 w-16" />
            <Skeleton className="mt-4 h-3 w-full" />
          </div>
        ))}
      </div>
    );
  }

  const { activeIncidents: a, responseTimes, last24h, events } = summary;
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <div className="rounded-lg border border-line bg-surface p-4">
        <div className="flex items-center justify-between">
          <p className="text-xs font-medium text-fg-muted">Active incidents</p>
          <Link href="/incidents" className="text-xs text-fg-subtle hover:text-fg">
            View all
          </Link>
        </div>
        <div className="mt-2 flex items-end gap-4">
          <p className="text-5xl font-semibold leading-none tracking-tight text-fg">{a.total}</p>
          <p className="pb-1 text-xs leading-relaxed text-fg-muted">
            <span className="tabular text-fg">{a.open}</span> open
            <br />
            <span className="tabular text-fg">{a.investigating}</span> investigating
          </p>
        </div>
        <div className="mt-3 grid grid-cols-2 gap-x-2 gap-y-0.5">
          {(["CRITICAL", "HIGH", "MEDIUM", "LOW"] as const).map((severity) => (
            <SeverityCount key={severity} severity={severity} count={a[severity.toLowerCase() as "critical"]} />
          ))}
        </div>
      </div>

      <Tile label="Needs an owner" href="/incidents?assignee=unassigned">
        <div className="flex items-center gap-3">
          <p className={cn("text-4xl font-semibold leading-none tracking-tight", a.unassigned ? "text-fg" : "text-fg-subtle")}>
            {a.unassigned}
          </p>
          <UserX className={cn("size-5", a.unassigned ? "text-warn" : "text-fg-subtle")} aria-hidden />
        </div>
        <p className="mt-2 text-xs text-fg-muted">unassigned active incidents</p>
        <p className="tabular mt-4 border-t border-line pt-3 text-xs text-fg-muted">
          Last 24h: <span className="text-fg">{last24h.created}</span> opened ·{" "}
          <span className="text-fg">{last24h.resolved}</span> resolved
        </p>
      </Tile>

      <Tile label={`Response times · ${responseTimes.windowDays}d`}>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <p className="text-3xl font-semibold leading-none tracking-tight text-fg">
              {formatDuration(responseTimes.mttaSeconds === null ? null : responseTimes.mttaSeconds * 1000)}
            </p>
            <p className="mt-2 text-xs text-fg-muted" title="Mean time to acknowledge">
              MTTA
            </p>
          </div>
          <div>
            <p className="text-3xl font-semibold leading-none tracking-tight text-fg">
              {formatDuration(responseTimes.mttrSeconds === null ? null : responseTimes.mttrSeconds * 1000)}
            </p>
            <p className="mt-2 text-xs text-fg-muted" title="Mean time to resolve">
              MTTR
            </p>
          </div>
        </div>
        <p className="mt-4 flex items-center gap-1.5 border-t border-line pt-3 text-xs text-fg-subtle">
          <Timer className="size-3.5" aria-hidden /> Acknowledge = first move to Investigating
        </p>
      </Tile>

      <Tile label="Event throughput" href="/events">
        <div className="flex items-baseline gap-2">
          <p className="text-4xl font-semibold leading-none tracking-tight text-fg">{compactNumber(events.lastHour)}</p>
          <p className="text-xs text-fg-muted">in the last hour</p>
        </div>
        <div className="mt-2">
          {volume.data ? <VolumeSparkline points={volume.data} /> : <Skeleton className="mt-1 h-[56px] w-full" />}
        </div>
      </Tile>
    </div>
  );
}
