import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { jsonResponse, makeIncident, makeUser } from "@/test/fixtures";
import { renderWithQuery } from "@/test/render";

import { IncidentActions } from "./incident-actions";

const me = { id: 2, name: "Aravind B", email: "operator@dispatch.local", role: "OPERATOR" as const };
const users = [makeUser(), makeUser({ id: 3, name: "Priya Raman" }), makeUser({ id: 6, name: "Daniel Okafor", active: false })];

describe("IncidentActions", () => {
  it("offers acknowledge and resolve for an open incident", () => {
    renderWithQuery(<IncidentActions incident={makeIncident()} users={users} me={me} />);

    expect(screen.getByRole("button", { name: /acknowledge/i })).toBeEnabled();
    expect(screen.getByRole("button", { name: /resolve/i })).toBeEnabled();
    expect(screen.queryByRole("button", { name: /reopen/i })).not.toBeInTheDocument();
    // Deactivated users cannot be picked as assignees.
    expect(screen.queryByRole("option", { name: "Daniel Okafor" })).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Aravind B (you)" })).toBeInTheDocument();
  });

  it("only offers reopen once resolved, and locks assignee and severity", () => {
    renderWithQuery(<IncidentActions incident={makeIncident({ status: "RESOLVED", resolvedAt: "2026-10-08T10:00:00Z" })} users={users} me={me} />);

    expect(screen.getByRole("button", { name: /reopen/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /acknowledge/i })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Assignee")).toBeDisabled();
    expect(screen.getByLabelText("Severity")).toBeDisabled();
  });

  it("acknowledging moves the incident to investigating and takes ownership when unassigned", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse(200, makeIncident({ status: "INVESTIGATING" })))
      .mockResolvedValueOnce(
        jsonResponse(200, makeIncident({ status: "INVESTIGATING", assignee: { id: 2, name: "Aravind B", email: me.email } })),
      );
    const user = userEvent.setup();
    renderWithQuery(<IncidentActions incident={makeIncident()} users={users} me={me} />);

    await user.click(screen.getByRole("button", { name: /acknowledge/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/\/incidents\/41\/status$/);
    expect(fetchMock.mock.calls[0][1]?.body).toBe(JSON.stringify({ status: "INVESTIGATING" }));
    expect(String(fetchMock.mock.calls[1][0])).toMatch(/\/incidents\/41\/assignee$/);
    expect(fetchMock.mock.calls[1][1]?.body).toBe(JSON.stringify({ assigneeId: 2 }));
  });

  it("resolving asks for an optional note and sends it", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(200, makeIncident({ status: "RESOLVED", resolvedAt: "2026-10-08T10:00:00Z" })));
    const user = userEvent.setup();
    renderWithQuery(<IncidentActions incident={makeIncident({ status: "INVESTIGATING" })} users={users} me={me} />);

    await user.click(screen.getByRole("button", { name: "Resolve" }));
    await user.type(await screen.findByLabelText("Resolution note"), "Replaced the SFP module");
    await user.click(screen.getByRole("button", { name: "Resolve incident" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(fetchMock.mock.calls[0][1]?.body).toBe(JSON.stringify({ status: "RESOLVED", note: "Replaced the SFP module" }));
  });
});
