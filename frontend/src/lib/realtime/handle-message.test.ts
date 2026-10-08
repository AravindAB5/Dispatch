import { describe, expect, it, vi } from "vitest";

import { makeEvent, makeSummary } from "@/test/fixtures";

import { handleRealtimeMessage, type RealtimeEffects } from "./handle-message";

function effects(): RealtimeEffects & { invalidated: string[] } {
  const invalidated: string[] = [];
  return {
    invalidated,
    invalidate: vi.fn((key) => invalidated.push(JSON.stringify(key))),
    pushEvent: vi.fn(),
    highlight: vi.fn(),
    notify: vi.fn(),
  };
}

const sentAt = "2026-10-08T09:41:20Z";

describe("handleRealtimeMessage", () => {
  it("event.created feeds the live stream and refreshes the incidents it touched", () => {
    const fx = effects();
    const event = makeEvent({ incidents: [{ id: 41, key: "INC-41", title: "t", severity: "CRITICAL", status: "OPEN", relation: "OCCURRENCE" }] });

    handleRealtimeMessage({ type: "event.created", data: event, sentAt }, fx);

    expect(fx.pushEvent).toHaveBeenCalledWith(event);
    expect(fx.invalidated).toContain(JSON.stringify(["incident", 41, "events"]));
    expect(fx.invalidated).toContain(JSON.stringify(["dashboard", "summary"]));
    expect(fx.notify).not.toHaveBeenCalled();
  });

  it("a new CRITICAL incident highlights, refreshes lists and raises a toast linking to it", () => {
    const fx = effects();
    handleRealtimeMessage({ type: "incident.created", data: makeSummary({ id: 77, key: "INC-77" }), sentAt }, fx);

    expect(fx.highlight).toHaveBeenCalledWith("incident:77");
    expect(fx.invalidated).toEqual(expect.arrayContaining([JSON.stringify(["incidents"]), JSON.stringify(["incident", 77]), JSON.stringify(["dashboard"])]));
    expect(fx.notify).toHaveBeenCalledWith(expect.objectContaining({ tone: "critical", href: "/incidents/77" }));
  });

  it("low-severity incidents and updates refresh quietly", () => {
    const fx = effects();
    handleRealtimeMessage({ type: "incident.created", data: makeSummary({ severity: "LOW" }), sentAt }, fx);
    handleRealtimeMessage({ type: "incident.updated", data: makeSummary(), sentAt }, fx);
    expect(fx.notify).not.toHaveBeenCalled();
    expect(fx.highlight).toHaveBeenCalledTimes(2);
  });

  it("announces a service going down and coming back", () => {
    const fx = effects();
    const change = { id: 5, name: "Notification Service", statusChangedAt: sentAt };
    handleRealtimeMessage({ type: "service.updated", data: { ...change, status: "DOWN", previousStatus: "HEALTHY" }, sentAt }, fx);
    handleRealtimeMessage({ type: "service.updated", data: { ...change, status: "HEALTHY", previousStatus: "DOWN" }, sentAt }, fx);

    expect(fx.notify).toHaveBeenNthCalledWith(1, expect.objectContaining({ tone: "critical", title: "Notification Service is down" }));
    expect(fx.notify).toHaveBeenNthCalledWith(2, expect.objectContaining({ tone: "success", title: "Notification Service recovered" }));
    expect(fx.invalidated).toContain(JSON.stringify(["devices"]));
  });

  it("a device status change refreshes devices and the service counts that include it", () => {
    const fx = effects();
    handleRealtimeMessage(
      {
        type: "device.updated",
        data: { id: 8, identifier: "DEVICE-108", name: "Gateway node 03", serviceId: 3, status: "ONLINE", previousStatus: "OFFLINE", lastSeenAt: sentAt },
        sentAt,
      },
      fx,
    );
    expect(fx.highlight).toHaveBeenCalledWith("device:8");
    expect(fx.invalidated).toEqual(expect.arrayContaining([JSON.stringify(["devices"]), JSON.stringify(["services"])]));
  });
});
