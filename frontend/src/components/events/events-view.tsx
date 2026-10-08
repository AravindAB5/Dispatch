"use client";

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { Inbox, Pause, Play, Send } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { SearchIcon, SeverityBadge } from "@/components/status/badges";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/form";
import { PageHeader } from "@/components/ui/panel";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/states";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { useIsAdmin } from "@/hooks/use-me";
import { eventsApi, servicesApi, type EventQuery } from "@/lib/api/endpoints";
import type { DispatchEvent, EventSeverity } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { EVENT_SEVERITIES } from "@/lib/domain";
import { matchesEventQuery, mergeLiveEvents } from "@/lib/events";
import { qk } from "@/lib/query-keys";
import { useRealtimeStore } from "@/stores/realtime-store";

import { EventDrawer } from "./event-drawer";
import { EventTable } from "./event-table";
import { SendEventDialog } from "./send-event-dialog";

const PAGE_SIZE = 50;
/** When this many live events have piled up above the fetched pages, refetch to stay in sync. */
const RESYNC_THRESHOLD = 100;

export function EventsView({ deviceId, serviceId, embedded }: { deviceId?: number; serviceId?: number; embedded?: boolean }) {
  const isAdmin = useIsAdmin();
  const [search, setSearch] = useState("");
  const q = useDebouncedValue(search.trim(), 300);
  const [type, setType] = useState("");
  const [severities, setSeverities] = useState<EventSeverity[]>([]);
  const [service, setService] = useState(serviceId ? String(serviceId) : "");
  const [live, setLive] = useState(true);
  const [frozen, setFrozen] = useState<DispatchEvent[]>([]);
  // While the pointer is over the list, new rows are held back so nothing shifts under the cursor.
  const [hoverFrozen, setHoverFrozen] = useState<DispatchEvent[] | null>(null);
  const [selected, setSelected] = useState<DispatchEvent | null>(null);
  const [sendOpen, setSendOpen] = useState(false);

  const query: EventQuery = useMemo(
    () => ({
      q: q || undefined,
      type: type ? [type] : undefined,
      severity: severities.length ? severities : undefined,
      serviceId: service ? Number(service) : undefined,
      deviceId,
      limit: PAGE_SIZE,
    }),
    [q, type, severities, service, deviceId],
  );

  const stream = useInfiniteQuery({
    queryKey: qk.events.stream(query),
    queryFn: ({ pageParam, signal }) => eventsApi.list({ ...query, cursor: pageParam }, signal),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  });
  const types = useQuery({ queryKey: qk.events.types, queryFn: eventsApi.types });
  const services = useQuery({ queryKey: qk.services.all, queryFn: servicesApi.list, enabled: !embedded });
  const liveEvents = useRealtimeStore((s) => s.liveEvents);

  const fetched = useMemo(() => stream.data?.pages.flatMap((p) => p.items) ?? [], [stream.data]);
  const fetchedIds = useMemo(() => new Set(fetched.map((e) => e.id)), [fetched]);
  const shownLive = live ? (hoverFrozen ?? liveEvents) : frozen;
  const events = useMemo(() => mergeLiveEvents(shownLive, fetched, query), [shownLive, fetched, query]);
  // Live events shown on top of the fetched pages (only those matching the current filters).
  const freshIds = useMemo(
    () => new Set(shownLive.filter((e) => !fetchedIds.has(e.id) && matchesEventQuery(e, query)).map((e) => e.id)),
    [shownLive, fetchedIds, query],
  );
  const pendingCount = live
    ? 0
    : liveEvents.filter((e) => !frozen.some((f) => f.id === e.id) && !fetchedIds.has(e.id) && matchesEventQuery(e, query)).length;

  // The live buffer is bounded; before it overflows, fold live events back into fetched pages.
  const { refetch, isFetching } = stream;
  useEffect(() => {
    if (live && !isFetching && freshIds.size > RESYNC_THRESHOLD) void refetch();
  }, [live, isFetching, freshIds.size, refetch]);

  function toggleLive() {
    if (live) setFrozen(liveEvents);
    setLive(!live);
  }

  return (
    <div>
      {!embedded && (
        <PageHeader
          title="Events"
          description="Everything Dispatch has received from devices, services and integrations."
          actions={
            <>
              <Button icon={live ? <Pause className="size-4" /> : <Play className="size-4" />} onClick={toggleLive}>
                {live ? "Pause stream" : "Resume"}
              </Button>
              {isAdmin && (
                <Button variant="primary" icon={<Send className="size-4" />} onClick={() => setSendOpen(true)}>
                  Send test event
                </Button>
              )}
            </>
          }
        />
      )}

      <div className={cn("rounded-lg border border-line bg-surface", embedded && "border-0 bg-transparent")}>
        <div className="flex flex-col gap-2 border-b border-line p-3 lg:flex-row lg:items-center">
          <div className="relative flex-1 lg:max-w-xs">
            <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-fg-subtle" />
            <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search source, type or message" className="pl-9" aria-label="Search events" />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Select aria-label="Event type" value={type} onChange={(e) => setType(e.target.value)} className="w-52">
              <option value="">All event types</option>
              {types.data?.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </Select>
            {!embedded && (
              <Select aria-label="Service" value={service} onChange={(e) => setService(e.target.value)} className="w-48">
                <option value="">All services</option>
                {services.data?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </Select>
            )}
            <div className="flex items-center gap-0.5" role="group" aria-label="Severity filter">
              {EVENT_SEVERITIES.map((severity) => {
                const on = severities.includes(severity);
                return (
                  <button
                    key={severity}
                    type="button"
                    aria-pressed={on}
                    onClick={() => setSeverities(on ? severities.filter((s) => s !== severity) : [...severities, severity])}
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
          </div>
          {live && (
            <span className="text-xs text-fg-subtle lg:ml-auto" aria-live="polite">
              {hoverFrozen ? "Holding new events while you read" : "Live"}
            </span>
          )}
          {embedded && (
            <button type="button" onClick={toggleLive} className="inline-flex cursor-pointer items-center gap-1.5 text-xs text-fg-muted hover:text-fg">
              {live ? <Pause className="size-3.5" /> : <Play className="size-3.5" />}
              {live ? "Pause" : "Resume"}
            </button>
          )}
        </div>

        {!live && (
          <button
            type="button"
            onClick={toggleLive}
            className="flex w-full cursor-pointer items-center justify-center gap-2 border-b border-line bg-accent/8 py-2 text-xs text-fg transition-colors hover:bg-accent/12"
          >
            <Pause className="size-3.5 text-accent" aria-hidden />
            Stream paused
            {pendingCount > 0 && (
              <>
                {" · "}
                <span className="font-semibold text-accent">{pendingCount >= 150 ? "150+" : pendingCount} new</span>
              </>
            )}{" "}
            — click to resume
          </button>
        )}

        {/* Always mounted, so leaving it always releases the hover hold — even if the content below changes. */}
        <div
          onPointerEnter={(e) => e.pointerType === "mouse" && setHoverFrozen(liveEvents)}
          onPointerLeave={() => setHoverFrozen(null)}
        >
        {stream.isPending ? (
          <SkeletonRows rows={10} />
        ) : stream.error ? (
          <ErrorState error={stream.error} title="Couldn't load events" onRetry={() => stream.refetch()} />
        ) : events.length === 0 ? (
          <EmptyState
            icon={<Inbox className="size-5" />}
            title="No events match"
            description={q || type || severities.length || service ? "Try widening the filters." : "Events will appear here as soon as they arrive."}
          />
        ) : (
          <>
            <EventTable events={events} freshIds={freshIds} onSelect={setSelected} />
            <div className="flex items-center justify-center border-t border-line py-3">
              {stream.hasNextPage ? (
                <Button size="sm" variant="ghost" loading={stream.isFetchingNextPage} onClick={() => stream.fetchNextPage()}>
                  Load older events
                </Button>
              ) : (
                <span className="text-xs text-fg-subtle">Beginning of the stream</span>
              )}
            </div>
          </>
        )}
        </div>
      </div>

      <EventDrawer event={selected} onClose={() => setSelected(null)} />
      {!embedded && <SendEventDialog open={sendOpen} onClose={() => setSendOpen(false)} />}
    </div>
  );
}
