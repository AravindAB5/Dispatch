"use client";

import { useQuery } from "@tanstack/react-query";
import { ChevronRight, FileQuestionMark, MapPin, Pencil } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { EventsView } from "@/components/events/events-view";
import { IncidentListPanel } from "@/components/incidents/incident-list-panel";
import { DeviceStatusBadge, HealthBadge } from "@/components/status/badges";
import { RelativeTime } from "@/components/status/relative-time";
import { Button, ButtonLink } from "@/components/ui/button";
import { Panel } from "@/components/ui/panel";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { useIsAdmin } from "@/hooks/use-me";
import { devicesApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/errors";
import { qk } from "@/lib/query-keys";

import { DeviceDialog } from "./device-dialog";

export function DeviceDetailView({ id }: { id: number }) {
  const isAdmin = useIsAdmin();
  const [editing, setEditing] = useState(false);
  const device = useQuery({ queryKey: qk.devices.detail(id), queryFn: () => devicesApi.get(id) });

  if (device.error) {
    return (
      <Panel>
        {device.error instanceof ApiError && device.error.status === 404 ? (
          <EmptyState icon={<FileQuestionMark className="size-5" />} title="Device not found" action={<ButtonLink href="/devices">Back to devices</ButtonLink>} />
        ) : (
          <ErrorState error={device.error} onRetry={() => device.refetch()} />
        )}
      </Panel>
    );
  }
  if (!device.data) return <Skeleton className="h-64 w-full rounded-lg" />;
  const d = device.data;

  return (
    <div>
      <nav aria-label="Breadcrumb" className="mb-3 flex items-center gap-1 text-xs text-fg-subtle">
        <Link href="/devices" className="hover:text-fg">
          Devices
        </Link>
        <ChevronRight className="size-3" aria-hidden />
        <span className="font-mono text-fg-muted">{d.identifier}</span>
      </nav>
      <div className="mb-5 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="font-mono text-2xl font-semibold tracking-tight text-fg">{d.identifier}</h1>
            <DeviceStatusBadge status={d.status} className="text-sm" />
          </div>
          <p className="mt-1 text-sm text-fg-muted">
            {d.name} · {d.deviceType}
          </p>
          <p className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-fg-muted">
            <span className="inline-flex items-center gap-2">
              <Link href={`/services/${d.service.id}`} className="text-fg hover:text-accent-strong">
                {d.service.name}
              </Link>
              <HealthBadge status={d.service.status} />
            </span>
            {d.location && (
              <span className="inline-flex items-center gap-1.5">
                <MapPin className="size-3.5 text-fg-subtle" aria-hidden /> {d.location}
              </span>
            )}
            <span>
              Last seen <RelativeTime iso={d.lastSeenAt} fallback="never" />
            </span>
          </p>
        </div>
        {isAdmin && (
          <Button icon={<Pencil className="size-4" />} onClick={() => setEditing(true)}>
            Edit device
          </Button>
        )}
      </div>

      <IncidentListPanel
        title="Incidents"
        description="Most recent first"
        query={{ deviceId: id, sort: "newest", size: 8, page: 0 }}
        viewAllHref={`/incidents?view=all&q=${d.identifier}`}
        emptyTitle="No incidents"
        emptyDescription={`${d.identifier} has never had an incident.`}
      />

      <h2 className="mb-3 mt-8 text-sm font-semibold text-fg">Events from this device</h2>
      <div className="rounded-lg border border-line bg-surface">
        <EventsView deviceId={id} embedded />
      </div>

      {editing && <DeviceDialog open onClose={() => setEditing(false)} device={d} />}
    </div>
  );
}
