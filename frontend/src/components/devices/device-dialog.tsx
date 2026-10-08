"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select } from "@/components/ui/form";
import { InlineError } from "@/components/ui/states";
import { devicesApi, servicesApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/errors";
import type { Device } from "@/lib/api/types";
import { qk } from "@/lib/query-keys";
import { toast } from "@/stores/toast-store";

export function DeviceDialog({ open, onClose, device }: { open: boolean; onClose: () => void; device?: Device }) {
  const queryClient = useQueryClient();
  const services = useQuery({ queryKey: qk.services.all, queryFn: servicesApi.list, enabled: open });
  const [identifier, setIdentifier] = useState(device?.identifier ?? "");
  const [name, setName] = useState(device?.name ?? "");
  const [deviceType, setDeviceType] = useState(device?.deviceType ?? "");
  const [serviceId, setServiceId] = useState(device ? String(device.service.id) : "");
  const [location, setLocation] = useState(device?.location ?? "");

  const save = useMutation({
    mutationFn: () => {
      const body = {
        identifier: identifier.trim(),
        name: name.trim(),
        deviceType: deviceType.trim(),
        serviceId: Number(serviceId),
        location: location.trim() || undefined,
      };
      return device ? devicesApi.update(device.id, body) : devicesApi.create(body);
    },
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: qk.devices.all });
      void queryClient.invalidateQueries({ queryKey: qk.services.all });
      toast.success(device ? "Device updated" : "Device registered", `${saved.identifier} · ${saved.name}`);
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
      title={device ? `Edit ${device.identifier}` : "Register a device"}
      description="Event producers reference devices by their identifier."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="device-form" variant="primary" loading={save.isPending} disabled={!identifier.trim() || !name.trim() || !serviceId || !deviceType.trim()}>
            {device ? "Save changes" : "Register device"}
          </Button>
        </>
      }
    >
      <form id="device-form" onSubmit={submit} className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Identifier" error={errors.identifier} hint={device ? "Cannot be changed" : "e.g. DEVICE-150"}>
            {(p) => (
              <Input {...p} value={identifier} onChange={(e) => setIdentifier(e.target.value.toUpperCase())} disabled={Boolean(device)} className="font-mono" maxLength={64} autoFocus={!device} />
            )}
          </Field>
          <Field label="Type" error={errors.deviceType}>
            {(p) => <Input {...p} value={deviceType} onChange={(e) => setDeviceType(e.target.value)} placeholder="Gateway node" maxLength={64} />}
          </Field>
        </div>
        <Field label="Name" error={errors.name}>
          {(p) => <Input {...p} value={name} onChange={(e) => setName(e.target.value)} maxLength={120} autoFocus={Boolean(device)} />}
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
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
          <Field label="Location" error={errors.location}>
            {(p) => <Input {...p} value={location} onChange={(e) => setLocation(e.target.value)} placeholder="Chennai DC · hall 2" maxLength={120} />}
          </Field>
        </div>
        {save.error && Object.keys(errors).length === 0 && <InlineError error={save.error} />}
      </form>
    </Dialog>
  );
}
