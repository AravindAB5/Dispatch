"use client";

import { useQuery } from "@tanstack/react-query";
import { ChevronRight, CircleCheck, FileQuestionMark, Undo2, Workflow } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { RecoveredChip, SeverityBadge, StatusBadge } from "@/components/status/badges";
import { RelativeTime } from "@/components/status/relative-time";
import { Button, ButtonLink } from "@/components/ui/button";
import { Panel } from "@/components/ui/panel";
import { EmptyState, ErrorState, Skeleton, SkeletonRows } from "@/components/ui/states";
import { useMe } from "@/hooks/use-me";
import { incidentsApi, usersApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/errors";
import { buildActivity } from "@/lib/incidents";
import { qk } from "@/lib/query-keys";
import { useFlash } from "@/hooks/use-flash";

import { ActivityTimeline, CommentComposer } from "./activity-timeline";
import { IncidentActions, ResolveDialog } from "./incident-actions";
import { IncidentDetailsPanel } from "./incident-details-panel";
import { RelatedEventList } from "./related-events";
import { useIncidentMutations } from "./use-incident-mutations";

export function IncidentDetailView({ id }: { id: number }) {
  const detail = useQuery({ queryKey: qk.incident.detail(id), queryFn: () => incidentsApi.get(id) });
  const history = useQuery({ queryKey: qk.incident.history(id), queryFn: () => incidentsApi.history(id) });
  const comments = useQuery({ queryKey: qk.incident.comments(id), queryFn: () => incidentsApi.comments(id) });
  const events = useQuery({ queryKey: qk.incident.events(id), queryFn: () => incidentsApi.events(id, 50) });
  const users = useQuery({ queryKey: qk.users, queryFn: usersApi.list });
  const { data: me } = useMe();
  const mutations = useIncidentMutations(id);
  const headerRef = useFlash<HTMLElement>(`incident:${id}`);
  const [resolveOpen, setResolveOpen] = useState(false);

  if (detail.error) {
    if (detail.error instanceof ApiError && detail.error.status === 404) {
      return (
        <Panel>
          <EmptyState
            icon={<FileQuestionMark className="size-5" />}
            title={`INC-${id} doesn't exist`}
            description="It may have been mistyped, or the link is from another environment."
            action={<ButtonLink href="/incidents">Back to incidents</ButtonLink>}
          />
        </Panel>
      );
    }
    return (
      <Panel>
        <ErrorState error={detail.error} title="Couldn't load this incident" onRetry={() => detail.refetch()} />
      </Panel>
    );
  }

  if (!detail.data) return <DetailSkeleton />;
  const incident = detail.data;
  const activity = history.data && comments.data ? buildActivity(history.data, comments.data) : null;
  const awaitingConfirmation = incident.recoveredAt && incident.status !== "RESOLVED";

  return (
    <div>
      <nav aria-label="Breadcrumb" className="mb-3 flex items-center gap-1 text-xs text-fg-subtle">
        <Link href="/incidents" className="hover:text-fg">
          Incidents
        </Link>
        <ChevronRight className="size-3" aria-hidden />
        <span className="font-mono text-fg-muted">{incident.key}</span>
      </nav>

      <header
        ref={headerRef}
        className="-mx-3 mb-5 rounded-lg px-3 py-2"
      >
        <div className="flex flex-wrap items-center gap-2">
          <SeverityBadge severity={incident.severity} />
          <StatusBadge status={incident.status} />
          {awaitingConfirmation && <RecoveredChip />}
          {incident.rule && (
            <span className="inline-flex items-center gap-1 text-xs text-fg-subtle">
              <Workflow className="size-3.5" aria-hidden /> {incident.rule.name}
            </span>
          )}
        </div>
        <h1 className="mt-3 text-2xl font-semibold tracking-tight text-fg">{incident.title}</h1>
        <p className="mt-1.5 text-sm text-fg-muted">
          <span className="font-mono text-fg-subtle">{incident.key}</span> · opened <RelativeTime iso={incident.createdAt} />
          {incident.service && <> · {incident.service.name}</>}
          {incident.device && <> · {incident.device.identifier}</>}
          {incident.eventCount > 0 && (
            <>
              {" "}
              · {incident.eventCount} {incident.eventCount === 1 ? "event" : "events"}
            </>
          )}
        </p>
        <div className="mt-4">
          <IncidentActions incident={incident} users={users.data ?? []} me={me} />
        </div>
      </header>

      {awaitingConfirmation && (
        <div className="mb-5 flex flex-col gap-3 rounded-lg border border-ok/30 bg-ok/8 px-4 py-3 sm:flex-row sm:items-center">
          <Undo2 className="size-5 shrink-0 text-ok" aria-hidden />
          <p className="flex-1 text-sm text-fg">
            The source reported recovery <RelativeTime iso={incident.recoveredAt} className="text-fg-muted" />. It was left{" "}
            {incident.status.toLowerCase()} so an operator can confirm the fix.
          </p>
          <Button size="sm" variant="primary" icon={<CircleCheck className="size-3.5" />} onClick={() => setResolveOpen(true)}>
            Confirm &amp; resolve
          </Button>
          {resolveOpen && (
            <ResolveDialog
              open
              onClose={() => setResolveOpen(false)}
              incident={incident}
              pending={mutations.changeStatus.isPending}
              onResolve={(note) =>
                mutations.changeStatus.mutate({ status: "RESOLVED", note }, { onSuccess: () => setResolveOpen(false) })
              }
            />
          )}
        </div>
      )}

      {incident.status === "RESOLVED" && (
        <div className="mb-5 flex items-start gap-3 rounded-lg border border-line-strong bg-surface px-4 py-3">
          <CircleCheck className="mt-0.5 size-5 shrink-0 text-st-resolved" aria-hidden />
          <div className="text-sm">
            <p className="text-fg">
              Resolved <RelativeTime iso={incident.resolvedAt} />
            </p>
            {incident.resolutionNote && <p className="mt-0.5 text-fg-muted">{incident.resolutionNote}</p>}
          </div>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="min-w-0 space-y-4">
          <Panel title="Summary">
            <p className="whitespace-pre-wrap text-sm leading-relaxed text-fg">
              {incident.description || <span className="text-fg-subtle">No description.</span>}
            </p>
            {incident.rule && (
              <p className="mt-3 border-t border-line pt-3 text-xs text-fg-muted">
                Opened by rule <span className="text-fg">{incident.rule.name}</span> when{" "}
                <code className="font-mono text-fg">{incident.rule.condition}</code>.{" "}
                {incident.rule.recoveryEventType ? (
                  <>
                    Recovers on <code className="font-mono text-fg">{incident.rule.recoveryEventType}</code>
                    {incident.rule.autoResolve
                      ? " — resolved automatically if nobody is investigating."
                      : " — an operator must confirm before it is resolved."}
                  </>
                ) : (
                  "This rule has no recovery signal; resolve it manually."
                )}
              </p>
            )}
          </Panel>

          <Panel title="Activity" description="Timeline of changes and notes">
            {activity ? (
              <ActivityTimeline items={activity} />
            ) : history.error || comments.error ? (
              <ErrorState error={history.error ?? comments.error} title="Couldn't load the timeline" />
            ) : (
              <SkeletonRows rows={4} />
            )}
            <div className="mt-6 border-t border-line pt-4">
              <CommentComposer
                pending={mutations.comment.isPending}
                error={mutations.comment.error}
                onSubmit={(body, done) => mutations.comment.mutate(body, { onSuccess: done })}
              />
            </div>
          </Panel>

          <Panel title="Related events" description="Events that opened, repeated or recovered this incident" bodyClassName="p-0">
            {events.data ? (
              events.data.total === 0 ? (
                <p className="px-4 py-6 text-center text-sm text-fg-subtle">
                  No linked events — this incident was declared manually.
                </p>
              ) : (
                <RelatedEventList data={events.data} />
              )
            ) : events.error ? (
              <ErrorState error={events.error} title="Couldn't load related events" onRetry={() => events.refetch()} />
            ) : (
              <SkeletonRows rows={4} />
            )}
          </Panel>
        </div>

        <div className="space-y-4">
          <IncidentDetailsPanel incident={incident} />
        </div>
      </div>
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div aria-busy>
      <Skeleton className="h-3 w-32" />
      <div className="mt-5 flex gap-2">
        <Skeleton className="h-5 w-20" />
        <Skeleton className="h-5 w-24" />
      </div>
      <Skeleton className="mt-4 h-7 w-2/3" />
      <Skeleton className="mt-3 h-4 w-1/2" />
      <div className="mt-8 grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
        <Skeleton className="h-80 w-full rounded-lg" />
        <Skeleton className="h-80 w-full rounded-lg" />
      </div>
    </div>
  );
}
