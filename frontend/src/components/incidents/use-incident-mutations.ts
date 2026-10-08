"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { incidentsApi } from "@/lib/api/endpoints";
import { describeError } from "@/lib/api/errors";
import type { IncidentDetail, IncidentSeverity, IncidentStatus } from "@/lib/api/types";
import { qk } from "@/lib/query-keys";
import { toast } from "@/stores/toast-store";

/**
 * Operator commands for one incident. Each success writes the returned incident straight into
 * the cache (instant UI) and refreshes the timeline and any lists that include it.
 */
export function useIncidentMutations(incidentId: number) {
  const queryClient = useQueryClient();

  const settle = (incident: IncidentDetail, message: string) => {
    queryClient.setQueryData(qk.incident.detail(incidentId), incident);
    void queryClient.invalidateQueries({ queryKey: qk.incident.history(incidentId) });
    void queryClient.invalidateQueries({ queryKey: qk.incidents.all });
    void queryClient.invalidateQueries({ queryKey: qk.dashboard.all });
    toast.success(message);
  };
  const fail = (title: string) => (error: unknown) => toast.error(title, describeError(error));

  const assign = useMutation({
    mutationFn: (assigneeId: number | null) => incidentsApi.assign(incidentId, assigneeId),
    onSuccess: (incident) =>
      settle(incident, incident.assignee ? `Assigned to ${incident.assignee.name}` : "Incident unassigned"),
    onError: fail("Couldn't change the assignee"),
  });

  const changeStatus = useMutation({
    mutationFn: ({ status, note }: { status: IncidentStatus; note?: string }) =>
      incidentsApi.changeStatus(incidentId, status, note),
    onSuccess: (incident, { status }) =>
      settle(
        incident,
        status === "RESOLVED" ? `${incident.key} resolved` : status === "INVESTIGATING" ? `${incident.key} acknowledged` : `${incident.key} reopened`,
      ),
    onError: fail("Couldn't change the status"),
  });

  const changeSeverity = useMutation({
    mutationFn: (severity: IncidentSeverity) => incidentsApi.changeSeverity(incidentId, severity),
    onSuccess: (incident) => settle(incident, `Severity set to ${incident.severity.toLowerCase()}`),
    onError: fail("Couldn't change the severity"),
  });

  /** Acknowledge = move to INVESTIGATING and, if nobody owns it yet, take ownership. */
  const acknowledge = useMutation({
    mutationFn: async ({ assignTo }: { assignTo: number | null }) => {
      let incident = await incidentsApi.changeStatus(incidentId, "INVESTIGATING");
      if (assignTo !== null && !incident.assignee) incident = await incidentsApi.assign(incidentId, assignTo);
      return incident;
    },
    onSuccess: (incident) => settle(incident, `${incident.key} acknowledged`),
    onError: fail("Couldn't acknowledge the incident"),
  });

  const comment = useMutation({
    mutationFn: (body: string) => incidentsApi.comment(incidentId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: qk.incident.comments(incidentId) });
      void queryClient.invalidateQueries({ queryKey: qk.incident.history(incidentId) });
    },
  });

  const busy = assign.isPending || changeStatus.isPending || changeSeverity.isPending || acknowledge.isPending;
  return { assign, changeStatus, changeSeverity, acknowledge, comment, busy };
}
