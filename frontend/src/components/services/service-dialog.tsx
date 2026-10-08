"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { InlineError } from "@/components/ui/states";
import { servicesApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/errors";
import type { Environment, Service } from "@/lib/api/types";
import { qk } from "@/lib/query-keys";
import { toast } from "@/stores/toast-store";

export function ServiceDialog({ open, onClose, service }: { open: boolean; onClose: () => void; service?: Service }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(service?.name ?? "");
  const [environment, setEnvironment] = useState<Environment>(service?.environment ?? "PRODUCTION");
  const [ownerTeam, setOwnerTeam] = useState(service?.ownerTeam ?? "");
  const [description, setDescription] = useState(service?.description ?? "");

  const save = useMutation({
    mutationFn: () => {
      const body = { name: name.trim(), environment, ownerTeam: ownerTeam.trim() || undefined, description: description.trim() || undefined };
      return service ? servicesApi.update(service.id, body) : servicesApi.create(body);
    },
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: qk.services.all });
      toast.success(service ? "Service updated" : "Service added", saved.name);
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
      title={service ? `Edit ${service.name}` : "Add a service"}
      description="Health is derived automatically from incidents and device connectivity."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="service-form" variant="primary" loading={save.isPending} disabled={!name.trim()}>
            {service ? "Save changes" : "Add service"}
          </Button>
        </>
      }
    >
      <form id="service-form" onSubmit={submit} className="space-y-4">
        <Field label="Name" error={errors.name}>
          {(p) => <Input {...p} value={name} onChange={(e) => setName(e.target.value)} maxLength={120} autoFocus />}
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Environment" error={errors.environment}>
            {(p) => (
              <Select {...p} value={environment} onChange={(e) => setEnvironment(e.target.value as Environment)}>
                <option value="PRODUCTION">Production</option>
                <option value="STAGING">Staging</option>
                <option value="DEVELOPMENT">Development</option>
              </Select>
            )}
          </Field>
          <Field label="Owner team" error={errors.ownerTeam}>
            {(p) => <Input {...p} value={ownerTeam} onChange={(e) => setOwnerTeam(e.target.value)} maxLength={120} placeholder="e.g. Edge Systems" />}
          </Field>
        </div>
        <Field label="Description" error={errors.description}>
          {(p) => <Textarea {...p} value={description} onChange={(e) => setDescription(e.target.value)} rows={3} maxLength={1000} />}
        </Field>
        {save.error && Object.keys(errors).length === 0 && <InlineError error={save.error} />}
      </form>
    </Dialog>
  );
}
