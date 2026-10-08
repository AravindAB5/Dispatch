"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Plus, SearchX, X } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { SearchIcon, SeverityBadge } from "@/components/status/badges";
import { Button } from "@/components/ui/button";
import { Input, SegmentedControl, Select } from "@/components/ui/form";
import { Pagination } from "@/components/ui/pagination";
import { PageHeader } from "@/components/ui/panel";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/states";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { useMe } from "@/hooks/use-me";
import { dashboardApi, incidentsApi, servicesApi, usersApi } from "@/lib/api/endpoints";
import { cn } from "@/lib/cn";
import { SEVERITIES } from "@/lib/domain";
import {
  DEFAULT_FILTERS,
  hasActiveFilters,
  parseIncidentFilters,
  serializeIncidentFilters,
  toIncidentQuery,
  type IncidentFilters,
  type IncidentView,
} from "@/lib/incidents";
import { qk } from "@/lib/query-keys";

import { DeclareIncidentDialog } from "./declare-incident-dialog";
import { IncidentTable } from "./incident-table";

const PAGE_SIZE = 20;

export function IncidentsView() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const filters = parseIncidentFilters(searchParams);
  const { data: me } = useMe();
  const [declareOpen, setDeclareOpen] = useState(false);

  const [search, setSearch] = useState(filters.q);
  const debouncedSearch = useDebouncedValue(search, 300);

  // Follow URL changes that did not come from the search box (sidebar link, back button, clear filters).
  const [urlQ, setUrlQ] = useState(filters.q);
  if (filters.q !== urlQ) {
    setUrlQ(filters.q);
    if (filters.q !== debouncedSearch.trim()) setSearch(filters.q);
  }

  function update(patch: Partial<IncidentFilters>) {
    const next = { ...filters, page: 1, ...patch };
    const params = serializeIncidentFilters(next);
    router.replace(params.size ? `${pathname}?${params}` : pathname, { scroll: false });
  }

  // Push the debounced search box value into the URL.
  useEffect(() => {
    if (debouncedSearch.trim() !== filters.q) update({ q: debouncedSearch.trim() });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch]);

  const query = toIncidentQuery(filters, me?.id, PAGE_SIZE);
  const waitingForMe = filters.assignee === "me" && !me;
  const incidents = useQuery({
    queryKey: qk.incidents.list(query),
    queryFn: ({ signal }) => incidentsApi.list(query, signal),
    placeholderData: keepPreviousData,
    enabled: !waitingForMe,
  });
  const summary = useQuery({ queryKey: qk.dashboard.summary, queryFn: dashboardApi.summary });
  const services = useQuery({ queryKey: qk.services.all, queryFn: servicesApi.list });
  const users = useQuery({ queryKey: qk.users, queryFn: usersApi.list });

  const counts = summary.data?.activeIncidents;
  const viewOptions: { value: IncidentView; label: string; count?: number }[] = [
    { value: "active", label: "Active", count: counts?.total },
    { value: "open", label: "Open", count: counts?.open },
    { value: "investigating", label: "Investigating", count: counts?.investigating },
    { value: "resolved", label: "Resolved" },
    { value: "all", label: "All" },
  ];

  return (
    <div>
      <PageHeader
        title="Incidents"
        description="Every actionable problem, from detection to resolution."
        actions={
          <Button variant="primary" icon={<Plus className="size-4" />} onClick={() => setDeclareOpen(true)}>
            Declare incident
          </Button>
        }
      />

      <div className="rounded-lg border border-line bg-surface">
        <div className="flex flex-col gap-3 border-b border-line p-3">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
            <SegmentedControl label="Status" value={filters.view} onChange={(view) => update({ view })} options={viewOptions} className="max-w-full self-start overflow-x-auto" />
            <div className="relative flex-1 lg:max-w-sm">
              <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-fg-subtle" />
              <Input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search title, INC-123 or DEVICE-042"
                className="pl-9"
                aria-label="Search incidents"
              />
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-1" role="group" aria-label="Severity filter">
              {SEVERITIES.map((severity) => {
                const on = filters.severities.includes(severity);
                return (
                  <button
                    key={severity}
                    type="button"
                    aria-pressed={on}
                    onClick={() =>
                      update({
                        severities: on ? filters.severities.filter((s) => s !== severity) : [...filters.severities, severity],
                      })
                    }
                    className={cn(
                      "cursor-pointer rounded-md border px-1 py-1 transition-colors",
                      on ? "border-line-strong bg-surface-3" : "border-transparent opacity-55 hover:opacity-100",
                    )}
                  >
                    <SeverityBadge severity={severity} size="sm" />
                  </button>
                );
              })}
            </div>
            <Select
              aria-label="Service"
              value={filters.serviceId ?? ""}
              onChange={(e) => update({ serviceId: e.target.value ? Number(e.target.value) : null })}
              className="w-44"
            >
              <option value="">All services</option>
              {services.data?.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
            <Select
              aria-label="Assignee"
              value={filters.assignee === null ? "" : filters.assignee === me?.id ? "me" : String(filters.assignee)}
              onChange={(e) => {
                const v = e.target.value;
                update({ assignee: v === "" ? null : v === "me" || v === "unassigned" ? v : Number(v) });
              }}
              className="w-44"
            >
              <option value="">Anyone</option>
              <option value="me">Assigned to me</option>
              <option value="unassigned">Unassigned</option>
              {users.data
                // Deactivated users are hidden unless a link filters by one of them.
                ?.filter((u) => u.id !== me?.id && (u.active || u.id === filters.assignee))
                .map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name}
                  </option>
                ))}
            </Select>
            <Select
              aria-label="Sort"
              value={filters.sort}
              onChange={(e) => update({ sort: e.target.value as IncidentFilters["sort"] })}
              className="w-44"
            >
              <option value="newest">Newest first</option>
              <option value="oldest">Oldest first</option>
              <option value="updated">Recently updated</option>
              <option value="severity">Most severe first</option>
            </Select>
            {hasActiveFilters(filters) && (
              <Button
                variant="ghost"
                size="sm"
                icon={<X className="size-3.5" />}
                onClick={() => {
                  setSearch("");
                  update({ ...DEFAULT_FILTERS, view: filters.view, sort: filters.sort });
                }}
              >
                Clear filters
              </Button>
            )}
          </div>
        </div>

        <div style={{ opacity: incidents.isPlaceholderData ? 0.6 : 1, transition: "opacity 150ms" }}>
          {incidents.isPending ? (
            <SkeletonRows rows={8} />
          ) : incidents.error ? (
            <ErrorState error={incidents.error} title="Couldn't load incidents" onRetry={() => incidents.refetch()} />
          ) : incidents.data.content.length === 0 && filters.page > 1 ? (
            // e.g. a stale link, or the last incidents on this page were resolved over the socket.
            <EmptyState
              icon={<SearchX className="size-5" />}
              title={`Page ${filters.page} is empty`}
              description={`There are ${incidents.data.totalElements} incidents in this view.`}
              action={<Button onClick={() => update({ page: 1 })}>Go to the first page</Button>}
            />
          ) : incidents.data.content.length === 0 ? (
            <EmptyState
              icon={<SearchX className="size-5" />}
              title={hasActiveFilters(filters) ? "No incidents match these filters" : "Nothing here"}
              description={
                hasActiveFilters(filters)
                  ? "Try a different search or clear the filters."
                  : filters.view === "active"
                    ? "No open or investigating incidents. Everything is quiet."
                    : "No incidents in this view yet."
              }
            />
          ) : (
            <>
              <IncidentTable incidents={incidents.data.content} />
              <Pagination
                page={incidents.data.page}
                size={incidents.data.size}
                total={incidents.data.totalElements}
                totalPages={incidents.data.totalPages}
                onPageChange={(page) => update({ page: page + 1 })}
                noun="incidents"
              />
            </>
          )}
        </div>
      </div>

      <DeclareIncidentDialog open={declareOpen} onClose={() => setDeclareOpen(false)} />
    </div>
  );
}
