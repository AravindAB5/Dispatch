"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AudioWaveform, Pause, Play } from "lucide-react";

import { useIsAdmin } from "@/hooks/use-me";
import { simulatorApi } from "@/lib/api/endpoints";
import { ApiError, describeError } from "@/lib/api/errors";
import { cn } from "@/lib/cn";
import { qk } from "@/lib/query-keys";
import { toast } from "@/stores/toast-store";

/**
 * Start/stop the development event simulator. Hidden entirely when the backend runs without it
 * (the endpoints return 404 there).
 */
export function SimulatorControl() {
  const isAdmin = useIsAdmin();
  const queryClient = useQueryClient();
  const { data, error } = useQuery({
    queryKey: qk.simulator,
    queryFn: simulatorApi.status,
    retry: false,
    staleTime: 60_000,
  });

  const toggle = useMutation({
    mutationFn: () => (data?.running ? simulatorApi.stop() : simulatorApi.start()),
    onSuccess: (status) => {
      queryClient.setQueryData(qk.simulator, status);
      toast.info(status.running ? "Simulator started" : "Simulator stopped",
        status.running ? "Synthetic telemetry is flowing into the pipeline." : "No more synthetic events will be generated.");
    },
    onError: (err) => toast.error("Simulator", describeError(err)),
  });

  if (error instanceof ApiError && error.status === 404) return null;
  if (!data) return null;

  const label = data.running ? "Simulator running" : "Simulator stopped";
  return (
    <div
      className="hidden h-8 items-center gap-2 rounded-md border border-line bg-surface pl-2.5 pr-1 text-xs sm:inline-flex"
      title={`${label} · ${data.eventsGenerated.toLocaleString()} events generated${data.activeProblems.length ? ` · active: ${data.activeProblems.join(", ")}` : ""}`}
    >
      <AudioWaveform className={cn("size-3.5", data.running ? "text-accent" : "text-fg-subtle")} aria-hidden />
      <span className={data.running ? "text-fg" : "text-fg-muted"}>Simulator</span>
      {isAdmin ? (
        <button
          type="button"
          onClick={() => toggle.mutate()}
          disabled={toggle.isPending}
          aria-label={data.running ? "Stop simulator" : "Start simulator"}
          className="inline-flex h-6 cursor-pointer items-center gap-1 rounded bg-surface-3 px-2 font-medium text-fg transition-colors hover:bg-[#2a2f36] disabled:opacity-50"
        >
          {data.running ? <Pause className="size-3" /> : <Play className="size-3" />}
          {data.running ? "Stop" : "Start"}
        </button>
      ) : (
        <span className="pr-1.5 text-fg-subtle">{data.running ? "on" : "off"}</span>
      )}
    </div>
  );
}
