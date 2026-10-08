"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { ArrowUpRight, CircleCheck } from "lucide-react";
import Link from "next/link";
import { useState, type FormEvent } from "react";

import { SeverityBadge } from "@/components/status/badges";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { InlineError } from "@/components/ui/states";
import { devicesApi, eventsApi, servicesApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/errors";
import type { EventSeverity, IncidentActionType, IngestionResponse, SourceType } from "@/lib/api/types";
import { EVENT_SEVERITIES, SEVERITY_LABEL } from "@/lib/domain";
import { qk } from "@/lib/query-keys";

type Preset = {
  label: string;
  eventType: string;
  severity: EventSeverity;
  sourceType: SourceType;
  message: string;
  metadata: Record<string, unknown>;
};

const PRESETS: Preset[] = [
  { label: "Device offline", eventType: "DEVICE_OFFLINE", severity: "CRITICAL", sourceType: "DEVICE", message: "Device stopped responding", metadata: { missedHeartbeats: 3 } },
  { label: "Device recovered", eventType: "DEVICE_RECOVERED", severity: "INFO", sourceType: "DEVICE", message: "Device reconnected", metadata: {} },
  { label: "CPU spike", eventType: "CPU_USAGE_HIGH", severity: "HIGH", sourceType: "DEVICE", message: "CPU usage reached 94%", metadata: { cpu: 94, threshold: 90 } },
  { label: "CPU normal", eventType: "CPU_USAGE_NORMAL", severity: "INFO", sourceType: "DEVICE", message: "CPU usage returned to normal (41%)", metadata: { cpu: 41 } },
  { label: "Service down", eventType: "SERVICE_DOWN", severity: "CRITICAL", sourceType: "SERVICE", message: "Health checks failing in all regions", metadata: { failingRegions: "all" } },
  { label: "Service recovered", eventType: "SERVICE_RECOVERED", severity: "INFO", sourceType: "SERVICE", message: "All health checks passing", metadata: {} },
];

const ACTION_TEXT: Record<IncidentActionType, string> = {
  CREATED: "Opened",
  DEDUPLICATED: "Absorbed into",
  RECURRED: "Recurrence on",
  RECOVERY_DETECTED: "Recovery flagged on",
  AUTO_RESOLVED: "Auto-resolved",
};

/**
 * Admin tool that sends an event through the real ingestion pipeline — the same endpoint external
 * producers use — and shows exactly what rule evaluation did with it.
 */
export function SendEventDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [preset, setPreset] = useState(0);
  const [eventType, setEventType] = useState(PRESETS[0].eventType);
  const [severity, setSeverity] = useState<EventSeverity>(PRESETS[0].severity);
  const [sourceType, setSourceType] = useState<SourceType>(PRESETS[0].sourceType);
  const [deviceId, setDeviceId] = useState("DEVICE-108");
  const [serviceId, setServiceId] = useState("");
  const [externalId, setExternalId] = useState("synthetic-check");
  const [message, setMessage] = useState(PRESETS[0].message);
  const [metadata, setMetadata] = useState(JSON.stringify(PRESETS[0].metadata, null, 2));
  const [metadataError, setMetadataError] = useState<string | null>(null);
  const [result, setResult] = useState<IngestionResponse | null>(null);

  const deviceQuery = { size: 100 };
  const devices = useQuery({ queryKey: qk.devices.list(deviceQuery), queryFn: () => devicesApi.list(deviceQuery), enabled: open });
  const services = useQuery({ queryKey: qk.services.all, queryFn: servicesApi.list, enabled: open });
  const types = useQuery({ queryKey: qk.events.types, queryFn: eventsApi.types, enabled: open });

  const send = useMutation({ mutationFn: eventsApi.ingest, onSuccess: setResult });
  const errors = send.error instanceof ApiError ? send.error.fieldErrors : {};

  function applyPreset(index: number) {
    const p = PRESETS[index];
    setPreset(index);
    setEventType(p.eventType);
    setSeverity(p.severity);
    setSourceType(p.sourceType);
    setMessage(p.message);
    setMetadata(JSON.stringify(p.metadata, null, 2));
    setMetadataError(null);
    if (p.sourceType === "SERVICE" && !serviceId && services.data?.length) setServiceId(String(services.data[0].id));
    send.reset();
    setResult(null);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    let parsed: Record<string, unknown> = {};
    try {
      parsed = metadata.trim() ? JSON.parse(metadata) : {};
      if (typeof parsed !== "object" || Array.isArray(parsed) || parsed === null) throw new Error();
      setMetadataError(null);
    } catch {
      setMetadataError("Metadata must be a JSON object, e.g. {\"cpu\": 94}");
      return;
    }
    const selectedService = services.data?.find((s) => String(s.id) === serviceId);
    setResult(null);
    send.mutate({
      eventType: eventType.trim(),
      severity,
      sourceType,
      sourceId:
        sourceType === "DEVICE"
          ? deviceId
          : sourceType === "SERVICE"
            ? (selectedService?.name ?? "service").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "")
            : externalId,
      serviceId: sourceType === "SERVICE" && serviceId ? Number(serviceId) : undefined,
      message: message.trim(),
      metadata: parsed,
    });
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Send a test event"
      description="Goes through the same ingestion pipeline as external producers."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Close
          </Button>
          <Button variant="primary" type="submit" form="send-event" loading={send.isPending}>
            Send event
          </Button>
        </>
      }
    >
      <form id="send-event" onSubmit={submit} className="space-y-4">
        <div className="flex flex-wrap gap-1.5">
          {PRESETS.map((p, i) => (
            <button
              key={p.label}
              type="button"
              onClick={() => applyPreset(i)}
              className={
                "cursor-pointer rounded-full border px-2.5 py-1 text-xs transition-colors " +
                (preset === i ? "border-accent/60 bg-accent/12 text-fg" : "border-line-strong text-fg-muted hover:text-fg")
              }
            >
              {p.label}
            </button>
          ))}
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Event type" error={errors.eventType}>
            {(p) => (
              <>
                <Input {...p} value={eventType} onChange={(e) => setEventType(e.target.value.toUpperCase())} list="known-event-types" className="font-mono" />
                <datalist id="known-event-types">
                  {types.data?.map((t) => <option key={t} value={t} />)}
                </datalist>
              </>
            )}
          </Field>
          <Field label="Severity" error={errors.severity}>
            {(p) => (
              <Select {...p} value={severity} onChange={(e) => setSeverity(e.target.value as EventSeverity)}>
                {EVENT_SEVERITIES.map((s) => (
                  <option key={s} value={s}>
                    {SEVERITY_LABEL[s]}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <Field label="Source type">
            {(p) => (
              <Select {...p} value={sourceType} onChange={(e) => setSourceType(e.target.value as SourceType)}>
                <option value="DEVICE">Device</option>
                <option value="SERVICE">Service</option>
                <option value="EXTERNAL">External</option>
              </Select>
            )}
          </Field>
          {sourceType === "DEVICE" && (
            <Field label="Device" error={errors.sourceId}>
              {(p) => (
                <Select {...p} value={deviceId} onChange={(e) => setDeviceId(e.target.value)}>
                  {devices.data?.content.map((d) => (
                    <option key={d.id} value={d.identifier}>
                      {d.identifier} · {d.service.name}
                    </option>
                  )) ?? <option value={deviceId}>{deviceId}</option>}
                </Select>
              )}
            </Field>
          )}
          {sourceType === "SERVICE" && (
            <Field label="Service" error={errors.serviceId}>
              {(p) => (
                <Select {...p} value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
                  <option value="">Choose a service</option>
                  {services.data?.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
          )}
          {sourceType === "EXTERNAL" && (
            <Field label="Source id" error={errors.sourceId}>
              {(p) => <Input {...p} value={externalId} onChange={(e) => setExternalId(e.target.value)} />}
            </Field>
          )}
        </div>
        <Field label="Message" error={errors.message}>
          {(p) => <Input {...p} value={message} onChange={(e) => setMessage(e.target.value)} maxLength={1000} />}
        </Field>
        <Field label="Metadata (JSON)" error={metadataError ?? errors.metadata}>
          {(p) => (
            <Textarea {...p} value={metadata} onChange={(e) => setMetadata(e.target.value)} rows={3} className="font-mono text-xs" spellCheck={false} />
          )}
        </Field>

        {send.error && Object.keys(errors).length === 0 && <InlineError error={send.error} />}

        {result && (
          <div className="rounded-md border border-ok/30 bg-ok/8 p-3" role="status">
            <p className="flex items-center gap-2 text-sm text-fg">
              <CircleCheck className="size-4 text-ok" aria-hidden /> Stored as event #{result.event.id}
            </p>
            {result.incidentActions.length === 0 ? (
              <p className="mt-1 pl-6 text-xs text-fg-muted">No alert rule matched, so no incident was touched.</p>
            ) : (
              <ul className="mt-2 space-y-1.5 pl-6">
                {result.incidentActions.map((action) => (
                  <li key={`${action.type}-${action.incidentId}`} className="flex items-center gap-2 text-xs">
                    <SeverityBadge severity={action.severity} size="sm" />
                    <span className="text-fg-muted">{ACTION_TEXT[action.type]}</span>
                    <Link href={`/incidents/${action.incidentId}`} className="inline-flex min-w-0 items-center gap-1 text-fg hover:text-accent-strong" onClick={onClose}>
                      <span className="font-mono">{action.key}</span>
                      <span className="truncate">{action.title}</span>
                      <ArrowUpRight className="size-3 shrink-0" />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </form>
    </Dialog>
  );
}
