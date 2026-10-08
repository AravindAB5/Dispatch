import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ActivityTimeline } from "@/components/incidents/activity-timeline";
import { buildActivity } from "@/lib/incidents";
import { makeComment, makeHistory } from "@/test/fixtures";

import { DeviceStatusBadge, HealthBadge, SeverityBadge, StatusBadge } from "./badges";

describe("status badges", () => {
  it("always carry a text label, never colour alone", () => {
    render(
      <>
        <SeverityBadge severity="CRITICAL" />
        <StatusBadge status="INVESTIGATING" />
        <HealthBadge status="DEGRADED" />
        <DeviceStatusBadge status="OFFLINE" />
      </>,
    );
    expect(screen.getByText("Critical")).toBeInTheDocument();
    expect(screen.getByText("Investigating")).toBeInTheDocument();
    expect(screen.getByText("Degraded")).toBeInTheDocument();
    expect(screen.getByText("Offline")).toBeInTheDocument();
  });
});

describe("ActivityTimeline", () => {
  it("attributes automatic changes to Dispatch and shows comment bodies", () => {
    const items = buildActivity(
      [
        makeHistory({ id: 1 }),
        makeHistory({ id: 2, action: "ASSIGNED", message: "Assigned to Aravind B", actor: { id: 2, name: "Aravind B", email: "a@x" }, createdAt: "2026-10-08T09:42:02Z" }),
      ],
      [makeComment({ body: "Power cycled via the PDU." })],
    );
    render(<ActivityTimeline items={items} />);

    expect(screen.getByText("Dispatch")).toBeInTheDocument();
    expect(screen.getByText("Assigned to Aravind B")).toBeInTheDocument();
    expect(screen.getByText("Power cycled via the PDU.")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(3);
  });
});
