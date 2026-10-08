"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { InlineError } from "@/components/ui/states";
import { devicesApi, incidentsApi, servicesApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/errors";
import type { IncidentSeverity } from "@/lib/api/types";
import { SEVERITIES, SEVERITY_LABEL } from "@/lib/domain";
import { qk } from "@/lib/query-keys";
import { toast } from "@/stores/toast-store";

/** For problems no rule detected — e.g. reported by customers or support. */
export function DeclareIncidentDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [title, setTitle] = useState("");
  const [severity, setSeverity] = useState<IncidentSeverity>("HIGH");
  const [serviceId, setServiceId] = useState("");
  const [deviceId, setDeviceId] = useState("");
  const [description, setDescription] = useState("");

  const services = useQuery({ queryKey: qk.services.all, queryFn: servicesApi.list, enabled: open });
  const deviceQuery = { serviceId: Number(serviceId), size: 100 };
  const devices = useQuery({
    queryKey: qk.devices.list(deviceQuery),
    queryFn: () => devicesApi.list(deviceQuery),
    enabled: open && Boolean(serviceId),
  });

  const declare = useMutation({
    mutationFn: () =>
      incidentsApi.declare({
        title: title.trim(),
        severity,
        description: description.trim() || undefined,
        serviceId: serviceId ? Number(serviceId) : null,
        deviceId: deviceId ? Number(deviceId) : null,
      }),
    onSuccess: (incident) => {
      void queryClient.invalidateQueries({ queryKey: qk.incidents.all });
      toast.success(`${incident.key} declared`, incident.title);
      onClose();
      router.push(`/incidents/${incident.id}`);
    },
  });
  const errors = declare.error instanceof ApiError ? declare.error.fieldErrors : {};

  function submit(event: FormEvent) {
    event.preventDefault();
    declare.mutate();
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Declare an incident"
      description="Open an incident by hand for a problem no alert rule has caught."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" form="declare-incident" loading={declare.isPending} disabled={!title.trim()}>
            Declare incident
          </Button>
        </>
      }
    >
      <form id="declare-incident" onSubmit={submit} className="space-y-4">
        <Field label="Title" error={errors.title}>
          {(p) => (
            <Input {...p} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Customers report failed logins" maxLength={200} autoFocus />
          )}
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Severity" error={errors.severity}>
            {(p) => (
              <Select {...p} value={severity} onChange={(e) => setSeverity(e.target.value as IncidentSeverity)}>
                {SEVERITIES.map((s) => (
                  <option key={s} value={s}>
                    {SEVERITY_LABEL[s]}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <Field label="Service" error={errors.serviceId}>
            {(p) => (
              <Select
                {...p}
                value={serviceId}
                onChange={(e) => {
                  setServiceId(e.target.value);
                  setDeviceId("");
                }}
              >
                <option value="">Not service-specific</option>
                {services.data?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </Select>
            )}
          </Field>
        </div>
        <Field label="Device" hint={serviceId ? "Optional" : "Choose a service first"} error={errors.deviceId}>
          {(p) => (
            <Select {...p} value={deviceId} onChange={(e) => setDeviceId(e.target.value)} disabled={!serviceId}>
              <option value="">Whole service</option>
              {devices.data?.content.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.identifier} — {d.name}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Description" error={errors.description}>
          {(p) => (
            <Textarea
              {...p}
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What is happening, who reported it, what has been checked so far…"
              maxLength={5000}
            />
          )}
        </Field>
        {declare.error && Object.keys(errors).length === 0 && <InlineError error={declare.error} />}
      </form>
    </Dialog>
  );
}
