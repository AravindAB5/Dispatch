"use client";

import { cn } from "@/lib/cn";
import { timeAgo } from "@/lib/format";
import { useNow } from "@/stores/clock-store";
import { useRealtimeStore, type ConnectionStatus } from "@/stores/realtime-store";

const STATES: Record<ConnectionStatus, { label: string; dot: string; text: string }> = {
  live: { label: "Live", dot: "bg-ok text-ok animate-pulse-ring", text: "text-fg" },
  connecting: { label: "Connecting", dot: "bg-warn", text: "text-fg-muted" },
  reconnecting: { label: "Reconnecting", dot: "bg-warn animate-pulse", text: "text-warn" },
  offline: { label: "Offline", dot: "bg-bad", text: "text-bad" },
};

/** Shows whether the real-time channel is up — operators must know if the screen can be trusted. */
export function ConnectionIndicator() {
  const status = useRealtimeStore((s) => s.status);
  const lastMessageAt = useRealtimeStore((s) => s.lastMessageAt);
  const now = useNow();
  const state = STATES[status];
  const detail =
    status === "live"
      ? `Real-time updates connected${lastMessageAt ? ` · last update ${timeAgo(new Date(lastMessageAt).toISOString(), now)}` : ""}`
      : status === "offline"
        ? "Real-time updates unavailable. Data may be stale."
        : "Connecting to real-time updates…";

  return (
    <div
      role="status"
      title={detail}
      className="inline-flex h-8 items-center gap-2 rounded-md border border-line bg-surface px-2.5 text-xs font-medium"
    >
      <span className={cn("size-2 rounded-full", state.dot)} aria-hidden />
      <span className={state.text}>{state.label}</span>
      <span className="sr-only">{detail}</span>
    </div>
  );
}
