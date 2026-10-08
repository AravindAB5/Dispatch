"use client";

import { useQuery } from "@tanstack/react-query";
import { ChevronRight, FileQuestionMark, Pencil } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { DeviceTable } from "@/components/devices/device-table";
import { EventsView } from "@/components/events/events-view";
import { IncidentListPanel } from "@/components/incidents/incident-list-panel";
import { HealthBadge } from "@/components/status/badges";
import { RelativeTime } from "@/components/status/relative-time";
import { Button, ButtonLink } from "@/components/ui/button";
import { Panel } from "@/components/ui/panel";
import { EmptyState, ErrorState, Skeleton, SkeletonRows } from "@/components/ui/states";
import { useIsAdmin } from "@/hooks/use-me";
import { devicesApi, servicesApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/errors";
import { qk } from "@/lib/query-keys";

import { DeviceMix } from "./device-mix";
import { ServiceDialog } from "./service-dialog";
import { ActiveIncidentCounts } from "./services-view";

export function ServiceDetailView({ id }: { id: number }) {
  const isAdmin = useIsAdmin();
  const [editing, setEditing] = useState(false);
  const service = useQuery({ queryKey: qk.services.detail(id), queryFn: () => servicesApi.get(id) });
  const deviceQuery = { serviceId: id, size: 100 };
  const devices = useQuery({ queryKey: qk.devices.list(deviceQuery), queryFn: () => devicesApi.list(deviceQuery) });

  if (service.error) {
    return (
      <Panel>
        {service.error instanceof ApiError && service.error.status === 404 ? (
          <EmptyState
            icon={<FileQuestionMark className="size-5" />}
            title="Service not found"
            action={<ButtonLink href="/services">Back to services</ButtonLink>}
          />
        ) : (
          <ErrorState error={service.error} onRetry={() => service.refetch()} />
        )}
      </Panel>
    );
  }
  if (!service.data) return <Skeleton className="h-64 w-full rounded-lg" />;
  const s = service.data;

  return (
    <div>
      <nav aria-label="Breadcrumb" className="mb-3 flex items-center gap-1 text-xs text-fg-subtle">
        <Link href="/services" className="hover:text-fg">
          Services
        </Link>
        <ChevronRight className="size-3" aria-hidden />
        <span className="text-fg-muted">{s.name}</span>
      </nav>
      <div className="mb-5 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold tracking-tight text-fg">{s.name}</h1>
            <HealthBadge status={s.status} className="text-sm" />
          </div>
          <p className="mt-1 text-sm text-fg-muted">
            {s.ownerTeam ?? "No owner"} · {s.environment.toLowerCase()} · {s.status.toLowerCase()} since{" "}
            <RelativeTime iso={s.statusChangedAt} />
          </p>
          {s.description && <p className="mt-3 max-w-2xl text-sm text-fg-muted">{s.description}</p>}
        </div>
        {isAdmin && (
          <Button icon={<Pencil className="size-4" />} onClick={() => setEditing(true)}>
            Edit service
          </Button>
        )}
      </div>

      <div className="mb-4 grid gap-4 sm:grid-cols-2">
        <Panel title="Devices">
          <DeviceMix devices={s.devices} />
        </Panel>
        <Panel title="Active incidents">
          <ActiveIncidentCounts counts={s.activeIncidents} />
        </Panel>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <IncidentListPanel
          title="Incidents"
          description="Active first, most severe first"
          query={{ serviceId: id, status: ["OPEN", "INVESTIGATING"], sort: "severity", size: 10, page: 0 }}
          viewAllHref={`/incidents?service=${id}`}
          emptyDescription={`${s.name} has no active incidents.`}
        />
        <Panel title="Devices" description={`${s.devices.total} registered`} bodyClassName="p-0">
          {devices.data ? (
            devices.data.content.length ? (
              <DeviceTable devices={devices.data.content} showService={false} />
            ) : (
              <p className="px-4 py-6 text-center text-sm text-fg-subtle">No devices belong to this service.</p>
            )
          ) : (
            <SkeletonRows rows={4} />
          )}
        </Panel>
      </div>

      <h2 className="mb-3 mt-8 text-sm font-semibold text-fg">Recent events</h2>
      <div className="rounded-lg border border-line bg-surface">
        <EventsView serviceId={id} embedded />
      </div>

      {editing && <ServiceDialog open onClose={() => setEditing(false)} service={s} />}
    </div>
  );
}
