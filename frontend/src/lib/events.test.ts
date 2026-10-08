import { describe, expect, it } from "vitest";

import { makeEvent } from "@/test/fixtures";

import { eventTypeLabel, matchesEventQuery, mergeLiveEvents, sourceLabel } from "./events";

describe("matchesEventQuery", () => {
  const event = makeEvent();

  it("matches when no filters are set", () => {
    expect(matchesEventQuery(event, {})).toBe(true);
  });

  it.each([
    [{ type: ["DEVICE_OFFLINE"] }, true],
    [{ type: ["CPU_USAGE_HIGH"] }, false],
    [{ severity: ["CRITICAL", "HIGH"] }, true],
    [{ severity: ["INFO"] }, false],
    [{ serviceId: 3 }, true],
    [{ serviceId: 4 }, false],
    [{ deviceId: 8 }, true],
    [{ q: "device-108" }, true],
    [{ q: "stopped resp" }, true],
    [{ q: "gateway down" }, false],
  ])("applies %o → %s", (query, expected) => {
    expect(matchesEventQuery(event, query)).toBe(expected);
  });
});

describe("mergeLiveEvents", () => {
  it("adds matching live events, removes duplicates and sorts newest first", () => {
    const fetched = [makeEvent({ id: 10, occurredAt: "2026-10-08T09:40:00Z" })];
    const live = [
      makeEvent({ id: 12, occurredAt: "2026-10-08T09:42:00Z" }),
      makeEvent({ id: 10, occurredAt: "2026-10-08T09:40:00Z", message: "duplicate" }),
      makeEvent({ id: 11, occurredAt: "2026-10-08T09:41:00Z", severity: "INFO", eventType: "DEVICE_HEARTBEAT" }),
    ];

    const merged = mergeLiveEvents(live, fetched, { severity: ["CRITICAL"] });

    expect(merged.map((e) => e.id)).toEqual([12, 10]);
    expect(merged[1].message).toBe("Device stopped responding"); // the fetched copy wins
  });

  it("breaks timestamp ties by id so ordering is stable", () => {
    const at = "2026-10-08T09:42:00Z";
    const merged = mergeLiveEvents([makeEvent({ id: 3, occurredAt: at })], [makeEvent({ id: 7, occurredAt: at })], {});
    expect(merged.map((e) => e.id)).toEqual([7, 3]);
  });
});

describe("labels", () => {
  it("humanises event types, keeping acronyms", () => {
    expect(eventTypeLabel("CPU_USAGE_HIGH")).toBe("CPU usage high");
    expect(eventTypeLabel("DEVICE_OFFLINE")).toBe("Device offline");
  });

  it("names the source by device, then service, then raw id", () => {
    expect(sourceLabel(makeEvent())).toBe("DEVICE-108");
    expect(sourceLabel(makeEvent({ device: null, sourceType: "SERVICE" }))).toBe("Device Gateway");
    expect(sourceLabel(makeEvent({ device: null, service: null, sourceType: "EXTERNAL", sourceId: "ci-pipeline" }))).toBe("ci-pipeline");
  });
});
