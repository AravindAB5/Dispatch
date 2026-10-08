"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";

import { HealthBadge } from "@/components/status/badges";
import { RelativeTime } from "@/components/status/relative-time";
import { Panel } from "@/components/ui/panel";
import { ErrorState, Skeleton } from "@/components/ui/states";
import { servicesApi } from "@/lib/api/endpoints";
import type { Service, ServiceStatus } from "@/lib/api/types";
import { qk } from "@/lib/query-keys";
import { useFlash } from "@/hooks/use-flash";

const ORDER: Record<ServiceStatus, number> = { DOWN: 0, DEGRADED: 1, HEALTHY: 2 };

export function sortByHealth(services: Service[]): Service[] {
  return [...services].sort((a, b) => ORDER[a.status] - ORDER[b.status] || a.name.localeCompare(b.name));
}

function ServiceRow({ service }: { service: Service }) {
  const ref = useFlash<HTMLLIElement>(`service:${service.id}`);
  const { devices, activeIncidents } = service;
  return (
    <li ref={ref}>
      <Link
        href={`/services/${service.id}`}
        className="flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-surface-2"
      >
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm text-fg">
            {service.name}
            {service.environment !== "PRODUCTION" && (
              <span className="ml-2 rounded bg-surface-3 px-1 py-px text-[10px] uppercase tracking-wide text-fg-subtle">
                {service.environment.slice(0, 4)}
              </span>
            )}
          </p>
          <p className="tabular truncate text-xs text-fg-subtle">
            {devices.total > 0 ? `${devices.online + devices.degraded}/${devices.total} devices up` : "No devices"}
            {activeIncidents.total > 0 && ` · ${activeIncidents.total} active`}
            {service.status !== "HEALTHY" && (
              <>
                {" · since "}
                <RelativeTime iso={service.statusChangedAt} />
              </>
            )}
          </p>
        </div>
        <HealthBadge status={service.status} />
      </Link>
    </li>
  );
}

export function ServiceHealthPanel({ className }: { className?: string }) {
  const { data, error, refetch, isPending } = useQuery({ queryKey: qk.services.all, queryFn: servicesApi.list });
  const counts = data?.reduce(
    (acc, s) => ({ ...acc, [s.status]: acc[s.status] + 1 }),
    { HEALTHY: 0, DEGRADED: 0, DOWN: 0 } as Record<ServiceStatus, number>,
  );

  return (
    <Panel
      title="Service health"
      description={
        counts
          ? `${counts.HEALTHY} healthy · ${counts.DEGRADED} degraded · ${counts.DOWN} down`
          : "Derived from active incidents and device connectivity"
      }
      actions={
        <Link href="/services" className="text-xs text-fg-subtle hover:text-fg">
          All services
        </Link>
      }
      bodyClassName="p-0"
      className={className}
    >
      {isPending ? (
        <div className="space-y-3 p-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-8 w-full" />
          ))}
        </div>
      ) : error ? (
        <ErrorState error={error} title="Couldn't load services" onRetry={() => refetch()} />
      ) : (
        <ul className="divide-y divide-line">
          {sortByHealth(data).map((service) => (
            <ServiceRow key={service.id} service={service} />
          ))}
        </ul>
      )}
    </Panel>
  );
}
