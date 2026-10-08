"use client";

import { CircleCheck, Hand, RotateCcw, Undo2 } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field, Select, Textarea } from "@/components/ui/form";
import type { AuthUser, IncidentDetail, IncidentSeverity, User } from "@/lib/api/types";
import { SEVERITIES, SEVERITY_LABEL } from "@/lib/domain";

import { useIncidentMutations } from "./use-incident-mutations";

type Props = {
  incident: IncidentDetail;
  users: User[];
  me: AuthUser | undefined;
};

/** The command bar on the incident page. Which commands appear depends on the lifecycle state. */
export function IncidentActions({ incident, users, me }: Props) {
  const m = useIncidentMutations(incident.id);
  const [resolveOpen, setResolveOpen] = useState(false);
  const resolved = incident.status === "RESOLVED";
  const assignable = users.filter((u) => u.active || u.id === incident.assignee?.id);

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Select
        aria-label="Assignee"
        value={incident.assignee?.id ?? ""}
        disabled={resolved || m.busy}
        onChange={(e) => m.assign.mutate(e.target.value ? Number(e.target.value) : null)}
        className="w-48"
        title={resolved ? "Reopen the incident to change the assignee" : undefined}
      >
        <option value="">Unassigned</option>
        {assignable.map((u) => (
          <option key={u.id} value={u.id}>
            {u.id === me?.id ? `${u.name} (you)` : u.name}
          </option>
        ))}
      </Select>
      <Select
        aria-label="Severity"
        value={incident.severity}
        disabled={resolved || m.busy}
        onChange={(e) => m.changeSeverity.mutate(e.target.value as IncidentSeverity)}
        className="w-32"
      >
        {SEVERITIES.map((s) => (
          <option key={s} value={s}>
            {SEVERITY_LABEL[s]}
          </option>
        ))}
      </Select>

      {incident.status === "OPEN" && (
        <Button
          icon={<Hand className="size-4" />}
          loading={m.acknowledge.isPending}
          disabled={m.busy}
          onClick={() => m.acknowledge.mutate({ assignTo: me?.id ?? null })}
          title="Move to Investigating, and assign to you if nobody owns it"
        >
          Acknowledge
        </Button>
      )}
      {incident.status === "INVESTIGATING" && (
        <Button
          variant="ghost"
          icon={<Undo2 className="size-4" />}
          disabled={m.busy}
          onClick={() => m.changeStatus.mutate({ status: "OPEN" })}
          title="Hand the incident back to the queue"
        >
          Back to open
        </Button>
      )}
      {!resolved && (
        <Button variant="primary" icon={<CircleCheck className="size-4" />} disabled={m.busy} onClick={() => setResolveOpen(true)}>
          Resolve
        </Button>
      )}
      {resolved && (
        <Button
          icon={<RotateCcw className="size-4" />}
          loading={m.changeStatus.isPending}
          onClick={() => m.changeStatus.mutate({ status: "OPEN" })}
        >
          Reopen
        </Button>
      )}

      {/* Mounted only while open, so a note from an earlier resolve never reappears. */}
      {resolveOpen && (
        <ResolveDialog
          open
          onClose={() => setResolveOpen(false)}
          incident={incident}
          pending={m.changeStatus.isPending}
          onResolve={(note) =>
            m.changeStatus.mutate({ status: "RESOLVED", note }, { onSuccess: () => setResolveOpen(false) })
          }
        />
      )}
    </div>
  );
}

export function ResolveDialog({
  open,
  onClose,
  incident,
  pending,
  onResolve,
}: {
  open: boolean;
  onClose: () => void;
  incident: IncidentDetail;
  pending: boolean;
  onResolve: (note: string | undefined) => void;
}) {
  const [note, setNote] = useState("");
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={`Resolve ${incident.key}`}
      description={incident.title}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={pending} onClick={() => onResolve(note.trim() || undefined)}>
            Resolve incident
          </Button>
        </>
      }
    >
      <Field label="Resolution note" hint="Optional. Shown on the incident and recorded in its history.">
        {(p) => (
          <Textarea
            {...p}
            rows={4}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="What fixed it? e.g. Replaced the faulty SFP module on switch port 14."
            maxLength={2000}
            autoFocus
          />
        )}
      </Field>
    </Dialog>
  );
}
