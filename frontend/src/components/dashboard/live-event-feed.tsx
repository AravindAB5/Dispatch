"use client";

import { useQuery } from "@tanstack/react-query";
import { Inbox } from "lucide-react";
import Link from "next/link";
import { useMemo } from "react";

import { EventFeedItem } from "@/components/events/event-feed-item";
import { Panel } from "@/components/ui/panel";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/states";
import { eventsApi } from "@/lib/api/endpoints";
import { cn } from "@/lib/cn";
import { mergeLiveEvents } from "@/lib/events";
import { qk } from "@/lib/query-keys";
import { useRealtimeStore } from "@/stores/realtime-store";

const QUERY = { limit: 25 };
const SHOWN = 25;

export function LiveEventFeed({ className }: { className?: string }) {
  const { data, error, isPending, refetch } = useQuery({
    queryKey: qk.events.stream(QUERY),
    queryFn: ({ signal }) => eventsApi.list(QUERY, signal),
  });
  const live = useRealtimeStore((s) => s.liveEvents);
  const status = useRealtimeStore((s) => s.status);

  const events = useMemo(() => mergeLiveEvents(live, data?.items ?? [], {}).slice(0, SHOWN), [live, data]);
  // Items that arrived over the socket animate in once, when they mount.
  const liveIds = useMemo(() => new Set(live.map((e) => e.id)), [live]);

  return (
    <Panel
      title={
        <span className="inline-flex items-center gap-2">
          Live events
          <span
            className={cn("size-1.5 rounded-full", status === "live" ? "bg-ok text-ok animate-pulse-ring" : "bg-fg-subtle")}
            aria-hidden
          />
        </span>
      }
      description="Everything received, as it arrives"
      actions={
        <Link href="/events" className="text-xs text-fg-subtle hover:text-fg">
          Open stream
        </Link>
      }
      bodyClassName="p-0"
      className={className}
    >
      {isPending ? (
        <SkeletonRows rows={7} />
      ) : error ? (
        <ErrorState error={error} title="Couldn't load events" onRetry={() => refetch()} />
      ) : events.length === 0 ? (
        <EmptyState icon={<Inbox className="size-5" />} title="No events yet" description="Events appear here the moment they are received." />
      ) : (
        <ul className="max-h-[528px] divide-y divide-line overflow-y-auto" aria-live="off">
          {events.map((event) => (
            <EventFeedItem key={event.id} event={event} fresh={liveIds.has(event.id)} />
          ))}
        </ul>
      )}
    </Panel>
  );
}
