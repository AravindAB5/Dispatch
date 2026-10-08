"use client";

import { useQuery } from "@tanstack/react-query";
import { ShieldCheck } from "lucide-react";
import Link from "next/link";

import { Panel } from "@/components/ui/panel";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/states";
import { incidentsApi, type IncidentQuery } from "@/lib/api/endpoints";
import { qk } from "@/lib/query-keys";

import { IncidentCompactRow } from "./incident-compact-row";

/** A titled, live-updating list of incidents for any query (dashboard, service and device pages). */
export function IncidentListPanel({
  title,
  description,
  query,
  viewAllHref,
  emptyTitle = "All clear",
  emptyDescription = "There are no incidents here right now.",
  className,
}: {
  title: string;
  description?: string;
  query: IncidentQuery;
  viewAllHref?: string;
  emptyTitle?: string;
  emptyDescription?: string;
  className?: string;
}) {
  const { data, error, isPending, refetch } = useQuery({
    queryKey: qk.incidents.list(query),
    queryFn: ({ signal }) => incidentsApi.list(query, signal),
  });

  return (
    <Panel
      title={title}
      description={description}
      actions={
        viewAllHref && (
          <Link href={viewAllHref} className="text-xs text-fg-subtle hover:text-fg">
            {data && data.totalElements > data.content.length ? `View all ${data.totalElements}` : "View all"}
          </Link>
        )
      }
      bodyClassName="p-0"
      className={className}
    >
      {isPending ? (
        <SkeletonRows rows={5} />
      ) : error ? (
        <ErrorState error={error} title="Couldn't load incidents" onRetry={() => refetch()} />
      ) : data.content.length === 0 ? (
        <EmptyState icon={<ShieldCheck className="size-5 text-ok" />} title={emptyTitle} description={emptyDescription} />
      ) : (
        <ul className="divide-y divide-line">
          {data.content.map((incident) => (
            <IncidentCompactRow key={incident.id} incident={incident} />
          ))}
        </ul>
      )}
    </Panel>
  );
}
