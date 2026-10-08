"use client";

import { IncidentListPanel } from "@/components/incidents/incident-list-panel";
import type { IncidentQuery } from "@/lib/api/endpoints";

const QUERY: IncidentQuery = { status: ["OPEN", "INVESTIGATING"], sort: "severity", size: 8, page: 0 };

export function ActiveIncidentsPanel({ className }: { className?: string }) {
  return (
    <IncidentListPanel
      title="Active incidents"
      description="Most severe first"
      query={QUERY}
      viewAllHref="/incidents"
      emptyDescription="There are no open or investigating incidents right now."
      className={className}
    />
  );
}
