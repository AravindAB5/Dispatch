"use client";

import { useQuery } from "@tanstack/react-query";
import { Boxes, Pencil, Plus } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { sortByHealth } from "@/components/dashboard/service-health-panel";
import { HealthBadge, SeverityBadge } from "@/components/status/badges";
import { RelativeTime } from "@/components/status/relative-time";
import { Button, IconButton } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/panel";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { useIsAdmin } from "@/hooks/use-me";
import { servicesApi } from "@/lib/api/endpoints";
import type { IncidentSeverity, Service } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { qk } from "@/lib/query-keys";
import { useFlash } from "@/hooks/use-flash";

import { DeviceMix } from "./device-mix";
import { ServiceDialog } from "./service-dialog";

export function ActiveIncidentCounts({ counts }: { counts: Service["activeIncidents"] }) {
  if (counts.total === 0) return <p className="text-xs text-fg-subtle">No active incidents</p>;
  const order: [IncidentSeverity, number][] = [
    ["CRITICAL", counts.critical],
    ["HIGH", counts.high],
    ["MEDIUM", counts.medium],
    ["LOW", counts.low],
  ];
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {order
        .filter(([, n]) => n > 0)
        .map(([severity, n]) => (
          <span key={severity} className="inline-flex items-center gap-1">
            <SeverityBadge severity={severity} size="sm" />
            <span className="tabular text-xs text-fg">×{n}</span>
          </span>
        ))}
    </div>
  );
}

function ServiceCard({ service, onEdit }: { service: Service; onEdit?: () => void }) {
  const ref = useFlash<HTMLElement>(`service:${service.id}`);
  return (
    <article
      ref={ref}
      className={cn(
        "group relative flex flex-col rounded-lg border bg-surface p-4 transition-colors hover:border-line-strong",
        service.status === "DOWN" ? "border-bad/40" : service.status === "DEGRADED" ? "border-warn/30" : "border-line",
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="truncate text-sm font-semibold text-fg">
            <Link href={`/services/${service.id}`} className="after:absolute after:inset-0 hover:text-accent-strong">
              {service.name}
            </Link>
          </h2>
          <p className="mt-0.5 truncate text-xs text-fg-subtle">
            {service.ownerTeam ?? "No owner"} · {service.environment.toLowerCase()}
          </p>
        </div>
        <HealthBadge status={service.status} />
      </div>
      <p className="mt-3 line-clamp-2 min-h-10 text-sm text-fg-muted">{service.description ?? "No description."}</p>
      <div className="mt-4 space-y-3 border-t border-line pt-3">
        <DeviceMix devices={service.devices} />
        <ActiveIncidentCounts counts={service.activeIncidents} />
      </div>
      <p className="mt-3 text-[11px] text-fg-subtle">
        {service.status === "HEALTHY" ? "Healthy" : service.status === "DOWN" ? "Down" : "Degraded"} since{" "}
        <RelativeTime iso={service.statusChangedAt} />
      </p>
      {onEdit && (
        <IconButton label={`Edit ${service.name}`} onClick={onEdit} className="absolute bottom-2.5 right-2.5 z-10 opacity-0 transition-opacity group-hover:opacity-100 focus:opacity-100">
          <Pencil className="size-3.5" />
        </IconButton>
      )}
    </article>
  );
}

export function ServicesView() {
  const isAdmin = useIsAdmin();
  const { data, error, isPending, refetch } = useQuery({ queryKey: qk.services.all, queryFn: servicesApi.list });
  const [dialog, setDialog] = useState<{ open: boolean; service?: Service }>({ open: false });

  return (
    <div>
      <PageHeader
        title="Services"
        description="Monitored services. Health is derived from active incidents and device connectivity."
        actions={
          isAdmin && (
            <Button variant="primary" icon={<Plus className="size-4" />} onClick={() => setDialog({ open: true })}>
              Add service
            </Button>
          )
        }
      />
      {isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-56 rounded-lg" />
          ))}
        </div>
      ) : error ? (
        <div className="rounded-lg border border-line bg-surface">
          <ErrorState error={error} title="Couldn't load services" onRetry={() => refetch()} />
        </div>
      ) : data.length === 0 ? (
        <div className="rounded-lg border border-line bg-surface">
          <EmptyState icon={<Boxes className="size-5" />} title="No services yet" description="Add the services you want Dispatch to monitor." />
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
          {sortByHealth(data).map((service) => (
            <ServiceCard key={service.id} service={service} onEdit={isAdmin ? () => setDialog({ open: true, service }) : undefined} />
          ))}
        </div>
      )}
      {dialog.open && <ServiceDialog open onClose={() => setDialog({ open: false })} service={dialog.service} />}
    </div>
  );
}
