"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Pencil, Plus, Trash, Workflow } from "lucide-react";
import { useState } from "react";

import { SeverityBadge } from "@/components/status/badges";
import { Button, IconButton } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Switch } from "@/components/ui/form";
import { PageHeader } from "@/components/ui/panel";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { useIsAdmin } from "@/hooks/use-me";
import { rulesApi } from "@/lib/api/endpoints";
import { describeError } from "@/lib/api/errors";
import type { Rule } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { CONDITION_LABEL } from "@/lib/domain";
import { qk } from "@/lib/query-keys";
import { toast } from "@/stores/toast-store";

import { RuleDialog } from "./rule-dialog";

function RuleCard({ rule, isAdmin, onEdit, onDelete }: { rule: Rule; isAdmin: boolean; onEdit: () => void; onDelete: () => void }) {
  const queryClient = useQueryClient();
  const toggle = useMutation({
    mutationFn: (enabled: boolean) => rulesApi.toggle(rule.id, enabled),
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: qk.rules });
      toast.success(saved.enabled ? "Rule enabled" : "Rule disabled", saved.name);
    },
    onError: (error) => toast.error("Couldn't update the rule", describeError(error)),
  });

  return (
    <article className={cn("rounded-lg border border-line bg-surface p-4 transition-opacity", !rule.enabled && "opacity-60")}>
      <div className="flex items-start gap-3">
        <Switch
          checked={rule.enabled}
          onChange={(enabled) => toggle.mutate(enabled)}
          label={rule.enabled ? `Disable ${rule.name}` : `Enable ${rule.name}`}
          disabled={!isAdmin || toggle.isPending}
        />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-sm font-semibold text-fg">{rule.name}</h2>
            {!rule.enabled && <span className="rounded bg-surface-3 px-1.5 py-px text-[10px] uppercase tracking-wide text-fg-subtle">Disabled</span>}
          </div>
          {rule.description && <p className="mt-1 text-sm text-fg-muted">{rule.description}</p>}

          <div className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-2 text-xs">
            <span className="text-fg-subtle">{CONDITION_LABEL[rule.conditionType]}</span>
            <code className="rounded border border-line-strong bg-canvas/70 px-1.5 py-0.5 font-mono text-[11.5px] text-fg">{rule.condition}</code>
            <ArrowRight className="size-3.5 text-fg-subtle" aria-hidden />
            <SeverityBadge severity={rule.incidentSeverity} size="sm" />
            {rule.titleTemplate && <code className="font-mono text-[11.5px] text-fg-muted">&ldquo;{rule.titleTemplate}&rdquo;</code>}
          </div>

          <dl className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-xs text-fg-muted">
            <div>
              <dt className="inline text-fg-subtle">Recovers on </dt>
              <dd className="inline">
                {rule.recoveryEventType ? (
                  <>
                    <code className="font-mono text-fg">{rule.recoveryEventType}</code>
                    {rule.autoResolve ? " · auto-resolves" : " · needs confirmation"}
                  </>
                ) : (
                  "manual resolution only"
                )}
              </dd>
            </div>
            <div>
              <dt className="inline text-fg-subtle">Scope </dt>
              <dd className="inline">{rule.service ? rule.service.name : "All services"}</dd>
            </div>
            <div>
              <dt className="inline text-fg-subtle">Incidents </dt>
              <dd className="tabular inline">
                <span className={rule.activeIncidents > 0 ? "text-fg" : undefined}>{rule.activeIncidents} active</span> ·{" "}
                {rule.totalIncidents} total
              </dd>
            </div>
          </dl>
        </div>
        {isAdmin && (
          <div className="flex shrink-0 items-center">
            <IconButton label={`Edit ${rule.name}`} onClick={onEdit}>
              <Pencil className="size-3.5" />
            </IconButton>
            <IconButton label={`Delete ${rule.name}`} onClick={onDelete} className="hover:text-bad">
              <Trash className="size-3.5" />
            </IconButton>
          </div>
        )}
      </div>
    </article>
  );
}

export function RulesView() {
  const isAdmin = useIsAdmin();
  const queryClient = useQueryClient();
  const rules = useQuery({ queryKey: qk.rules, queryFn: rulesApi.list });
  const [editing, setEditing] = useState<{ open: boolean; rule?: Rule }>({ open: false });
  const [deleting, setDeleting] = useState<Rule | null>(null);

  const remove = useMutation({
    mutationFn: (rule: Rule) => rulesApi.remove(rule.id),
    onSuccess: (_, rule) => {
      void queryClient.invalidateQueries({ queryKey: qk.rules });
      toast.success("Rule deleted", rule.name);
      setDeleting(null);
    },
    onError: (error) => toast.error("Couldn't delete the rule", describeError(error)),
  });

  return (
    <div>
      <PageHeader
        title="Alert rules"
        description="Rules decide which events become incidents. Events for the same rule and source fold into one active incident."
        actions={
          isAdmin && (
            <Button variant="primary" icon={<Plus className="size-4" />} onClick={() => setEditing({ open: true })}>
              New rule
            </Button>
          )
        }
      />
      {!isAdmin && (
        <p className="mb-4 rounded-md border border-line bg-surface px-3 py-2 text-xs text-fg-muted">
          Rules are managed by administrators. You can view how incidents are detected.
        </p>
      )}
      {rules.isPending ? (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-32 rounded-lg" />
          ))}
        </div>
      ) : rules.error ? (
        <div className="rounded-lg border border-line bg-surface">
          <ErrorState error={rules.error} title="Couldn't load rules" onRetry={() => rules.refetch()} />
        </div>
      ) : rules.data.length === 0 ? (
        <div className="rounded-lg border border-line bg-surface">
          <EmptyState
            icon={<Workflow className="size-5" />}
            title="No alert rules"
            description="Without rules, events are recorded but never become incidents."
            action={isAdmin && <Button onClick={() => setEditing({ open: true })}>Create the first rule</Button>}
          />
        </div>
      ) : (
        <div className="space-y-3">
          {[...rules.data].sort((a, b) => Number(b.enabled) - Number(a.enabled)).map((rule) => (
            <RuleCard
              key={rule.id}
              rule={rule}
              isAdmin={isAdmin}
              onEdit={() => setEditing({ open: true, rule })}
              onDelete={() => setDeleting(rule)}
            />
          ))}
        </div>
      )}

      {editing.open && <RuleDialog open onClose={() => setEditing({ open: false })} rule={editing.rule} />}

      <Dialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        title="Delete rule?"
        description={deleting?.name}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDeleting(null)}>
              Cancel
            </Button>
            <Button variant="danger" loading={remove.isPending} onClick={() => deleting && remove.mutate(deleting)}>
              Delete rule
            </Button>
          </>
        }
      >
        <p className="text-sm text-fg-muted">
          Incidents it already opened are kept, but they will no longer resolve automatically. Disabling the rule instead
          keeps recovery working.
        </p>
      </Dialog>
    </div>
  );
}
