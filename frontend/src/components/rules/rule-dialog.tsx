"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";

import { SeverityBadge } from "@/components/status/badges";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, SegmentedControl, Select, Switch, Textarea } from "@/components/ui/form";
import { InlineError } from "@/components/ui/states";
import { eventsApi, rulesApi, servicesApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/errors";
import type { ComparisonOperator, EventSeverity, IncidentSeverity, Rule, RuleConditionType, RuleRequest } from "@/lib/api/types";
import { EVENT_SEVERITIES, OPERATOR_SYMBOL, SEVERITIES, SEVERITY_LABEL, TITLE_PLACEHOLDERS } from "@/lib/domain";
import { qk } from "@/lib/query-keys";
import { toast } from "@/stores/toast-store";

/** Plain-language rendering of the rule being edited. */
export function describeRule(r: Pick<RuleRequest, "conditionType" | "eventType" | "metricKey" | "operator" | "threshold" | "minEventSeverity">): string {
  switch (r.conditionType) {
    case "EVENT_TYPE":
      return `${r.eventType || "<event type>"} is received`;
    case "METRIC_THRESHOLD":
      return `${r.metricKey || "<metric>"} ${r.operator ? OPERATOR_SYMBOL[r.operator] : "?"} ${r.threshold ?? "?"} on ${r.eventType || "<event type>"}`;
    case "SEVERITY_AT_LEAST":
      return `${r.eventType || "any event"} has severity ≥ ${r.minEventSeverity ?? "?"}`;
  }
}

export function RuleDialog({ open, onClose, rule }: { open: boolean; onClose: () => void; rule?: Rule }) {
  const queryClient = useQueryClient();
  const services = useQuery({ queryKey: qk.services.all, queryFn: servicesApi.list, enabled: open });
  const types = useQuery({ queryKey: qk.events.types, queryFn: eventsApi.types, enabled: open });

  const [name, setName] = useState(rule?.name ?? "");
  const [description, setDescription] = useState(rule?.description ?? "");
  const [conditionType, setConditionType] = useState<RuleConditionType>(rule?.conditionType ?? "EVENT_TYPE");
  const [eventType, setEventType] = useState(rule?.eventType ?? "");
  const [metricKey, setMetricKey] = useState(rule?.metricKey ?? "");
  const [operator, setOperator] = useState<ComparisonOperator>(rule?.operator ?? "GT");
  const [threshold, setThreshold] = useState(rule?.threshold?.toString() ?? "");
  const [minEventSeverity, setMinEventSeverity] = useState<EventSeverity>(rule?.minEventSeverity ?? "CRITICAL");
  const [serviceId, setServiceId] = useState(rule?.service ? String(rule.service.id) : "");
  const [incidentSeverity, setIncidentSeverity] = useState<IncidentSeverity>(rule?.incidentSeverity ?? "HIGH");
  const [titleTemplate, setTitleTemplate] = useState(rule?.titleTemplate ?? "");
  const [recoveryEventType, setRecoveryEventType] = useState(rule?.recoveryEventType ?? "");
  const [autoResolve, setAutoResolve] = useState(rule?.autoResolve ?? true);
  const [enabled, setEnabled] = useState(rule?.enabled ?? true);

  const body: RuleRequest = {
    name: name.trim(),
    description: description.trim() || null,
    conditionType,
    eventType: eventType.trim() || null,
    metricKey: conditionType === "METRIC_THRESHOLD" ? metricKey.trim() || null : null,
    operator: conditionType === "METRIC_THRESHOLD" ? operator : null,
    threshold: conditionType === "METRIC_THRESHOLD" && threshold.trim() !== "" ? Number(threshold) : null,
    minEventSeverity: conditionType === "SEVERITY_AT_LEAST" ? minEventSeverity : null,
    serviceId: serviceId ? Number(serviceId) : null,
    incidentSeverity,
    titleTemplate: titleTemplate.trim() || null,
    recoveryEventType: recoveryEventType.trim() || null,
    autoResolve,
    enabled,
  };

  const save = useMutation({
    mutationFn: () => (rule ? rulesApi.update(rule.id, body) : rulesApi.create(body)),
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: qk.rules });
      toast.success(rule ? "Rule updated" : "Rule created", saved.name);
      onClose();
    },
  });
  const errors = save.error instanceof ApiError ? save.error.fieldErrors : {};

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate();
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={rule ? `Edit rule` : "New alert rule"}
      description="Matching events open an incident; repeats from the same source join it."
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="rule-form" variant="primary" loading={save.isPending} disabled={!name.trim()}>
            {rule ? "Save rule" : "Create rule"}
          </Button>
        </>
      }
    >
      <form id="rule-form" onSubmit={submit} className="space-y-5">
        <div className="rounded-md border border-line bg-canvas/60 p-3 font-mono text-xs leading-relaxed text-fg-muted">
          <p>
            <span className="text-accent">WHEN</span> {describeRule(body)}
            {serviceId && services.data && (
              <> for {services.data.find((s) => String(s.id) === serviceId)?.name}</>
            )}
          </p>
          <p>
            <span className="text-accent">THEN</span> open a{" "}
            <SeverityBadge severity={incidentSeverity} size="sm" className="align-middle" /> incident
          </p>
          <p>
            <span className="text-accent">UNTIL</span>{" "}
            {recoveryEventType ? `${recoveryEventType}${autoResolve ? " (auto-resolve if not being investigated)" : " (operator confirms)"}` : "an operator resolves it"}
          </p>
        </div>

        <Field label="Name" error={errors.name}>
          {(p) => <Input {...p} value={name} onChange={(e) => setName(e.target.value)} maxLength={120} placeholder="High CPU usage" />}
        </Field>

        <div>
          <p className="mb-1.5 text-xs font-medium text-fg-muted">Condition</p>
          <SegmentedControl
            label="Condition type"
            value={conditionType}
            onChange={setConditionType}
            options={[
              { value: "EVENT_TYPE", label: "Event type" },
              { value: "METRIC_THRESHOLD", label: "Metric threshold" },
              { value: "SEVERITY_AT_LEAST", label: "Minimum severity" },
            ]}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            label={conditionType === "SEVERITY_AT_LEAST" ? "Event type (optional)" : "Event type"}
            error={errors.eventType}
          >
            {(p) => (
              <>
                <Input {...p} value={eventType} onChange={(e) => setEventType(e.target.value.toUpperCase())} list="rule-event-types" className="font-mono" placeholder="DEVICE_OFFLINE" />
                <datalist id="rule-event-types">{types.data?.map((t) => <option key={t} value={t} />)}</datalist>
              </>
            )}
          </Field>
          {conditionType === "SEVERITY_AT_LEAST" && (
            <Field label="Minimum event severity" error={errors.minEventSeverity}>
              {(p) => (
                <Select {...p} value={minEventSeverity} onChange={(e) => setMinEventSeverity(e.target.value as EventSeverity)}>
                  {EVENT_SEVERITIES.map((s) => (
                    <option key={s} value={s}>
                      {SEVERITY_LABEL[s]}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
          )}
        </div>

        {conditionType === "METRIC_THRESHOLD" && (
          <div className="grid gap-4 sm:grid-cols-[1fr_96px_1fr]">
            <Field label="Metadata key" error={errors.metricKey} hint="e.g. cpu, disk.root">
              {(p) => <Input {...p} value={metricKey} onChange={(e) => setMetricKey(e.target.value)} className="font-mono" placeholder="cpu" />}
            </Field>
            <Field label="Operator" error={errors.operator}>
              {(p) => (
                <Select {...p} value={operator} onChange={(e) => setOperator(e.target.value as ComparisonOperator)}>
                  {(Object.keys(OPERATOR_SYMBOL) as ComparisonOperator[]).map((op) => (
                    <option key={op} value={op}>
                      {OPERATOR_SYMBOL[op]}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label="Threshold" error={errors.threshold}>
              {(p) => <Input {...p} type="number" step="any" value={threshold} onChange={(e) => setThreshold(e.target.value)} placeholder="90" />}
            </Field>
          </div>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Incident severity" error={errors.incidentSeverity}>
            {(p) => (
              <Select {...p} value={incidentSeverity} onChange={(e) => setIncidentSeverity(e.target.value as IncidentSeverity)}>
                {SEVERITIES.map((s) => (
                  <option key={s} value={s}>
                    {SEVERITY_LABEL[s]}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <Field label="Scope" error={errors.serviceId}>
            {(p) => (
              <Select {...p} value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
                <option value="">All services</option>
                {services.data?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </Select>
            )}
          </Field>
        </div>

        <Field
          label="Incident title template"
          error={errors.titleTemplate}
          hint={<>Placeholders: {TITLE_PLACEHOLDERS.map((p) => `{${p}}`).join(" ")}</>}
        >
          {(p) => <Input {...p} value={titleTemplate} onChange={(e) => setTitleTemplate(e.target.value)} placeholder="{source} is offline" maxLength={200} />}
        </Field>

        <div className="grid gap-4 sm:grid-cols-2 sm:items-end">
          <Field label="Recovery event type" error={errors.recoveryEventType} hint="Optional signal that the problem cleared">
            {(p) => (
              <Input {...p} value={recoveryEventType} onChange={(e) => setRecoveryEventType(e.target.value.toUpperCase())} list="rule-event-types" className="font-mono" placeholder="DEVICE_RECOVERED" />
            )}
          </Field>
          <div className="space-y-3 pb-6">
            <label className="flex items-center justify-between gap-3 text-sm text-fg">
              Auto-resolve on recovery
              <Switch checked={autoResolve} onChange={setAutoResolve} label="Auto-resolve on recovery" disabled={!recoveryEventType} />
            </label>
            <label className="flex items-center justify-between gap-3 text-sm text-fg">
              Enabled
              <Switch checked={enabled} onChange={setEnabled} label="Rule enabled" />
            </label>
          </div>
        </div>

        <Field label="Description" error={errors.description}>
          {(p) => <Textarea {...p} value={description} onChange={(e) => setDescription(e.target.value)} rows={2} maxLength={1000} />}
        </Field>

        {save.error && Object.keys(errors).length === 0 && <InlineError error={save.error} />}
      </form>
    </Dialog>
  );
}
