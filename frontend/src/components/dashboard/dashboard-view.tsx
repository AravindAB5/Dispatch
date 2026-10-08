"use client";

import { useQuery } from "@tanstack/react-query";
import { ChartColumnStacked, Table2 } from "lucide-react";
import { useState } from "react";

import { RelativeTime } from "@/components/status/relative-time";
import { Panel, PageHeader } from "@/components/ui/panel";
import { SegmentedControl } from "@/components/ui/form";
import { ErrorState, Skeleton } from "@/components/ui/states";
import { dashboardApi } from "@/lib/api/endpoints";
import { localTimeZone } from "@/lib/format";
import { qk } from "@/lib/query-keys";

import { ActiveIncidentsPanel } from "./active-incidents-panel";
import { IncidentTrendChart, TrendLegend } from "./incident-trend-chart";
import { LiveEventFeed } from "./live-event-feed";
import { ServiceHealthPanel } from "./service-health-panel";
import { StatTiles } from "./stat-tiles";

const TREND_HOURS = 24;

export function DashboardView() {
  const [tz] = useState(localTimeZone);
  const [trendView, setTrendView] = useState<"chart" | "table">("chart");
  const summary = useQuery({ queryKey: qk.dashboard.summary, queryFn: dashboardApi.summary });
  const trend = useQuery({ queryKey: qk.dashboard.trend(TREND_HOURS, tz), queryFn: () => dashboardApi.trend(TREND_HOURS, tz) });

  return (
    <div>
      <PageHeader
        title="Operations overview"
        description="Incidents, service health and telemetry — updated live as events arrive."
        actions={
          summary.data && (
            <span className="text-xs text-fg-subtle">
              Updated <RelativeTime iso={summary.data.generatedAt} />
            </span>
          )
        }
      />

      {summary.error ? (
        <Panel>
          <ErrorState error={summary.error} title="Couldn't load the dashboard" onRetry={() => summary.refetch()} />
        </Panel>
      ) : (
        <StatTiles summary={summary.data} />
      )}

      <div className="mt-4 grid gap-4 xl:grid-cols-3">
        <Panel
          className="xl:col-span-2"
          title="Incident activity"
          description={`Opened and resolved per hour · last ${TREND_HOURS} hours`}
          actions={
            <SegmentedControl
              label="Trend view"
              value={trendView}
              onChange={setTrendView}
              options={[
                { value: "chart", label: <ChartColumnStacked className="size-3.5" aria-hidden />, ariaLabel: "Chart view" },
                { value: "table", label: <Table2 className="size-3.5" aria-hidden />, ariaLabel: "Table view" },
              ]}
            />
          }
        >
          <TrendLegend />
          <div className="mt-3" style={{ opacity: trend.isFetching && trend.data ? 0.7 : 1, transition: "opacity 150ms" }}>
            {trend.error ? (
              <ErrorState error={trend.error} title="Couldn't load the trend" onRetry={() => trend.refetch()} />
            ) : trend.data ? (
              <IncidentTrendChart points={trend.data} view={trendView} />
            ) : (
              <Skeleton className="h-[220px] w-full" />
            )}
          </div>
        </Panel>
        <ServiceHealthPanel />
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-3">
        <ActiveIncidentsPanel className="xl:col-span-2" />
        <LiveEventFeed />
      </div>
    </div>
  );
}
