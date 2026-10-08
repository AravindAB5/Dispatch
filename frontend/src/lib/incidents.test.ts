import { describe, expect, it } from "vitest";

import { makeComment, makeHistory } from "@/test/fixtures";

import {
  DEFAULT_FILTERS,
  buildActivity,
  hasActiveFilters,
  parseIncidentFilters,
  serializeIncidentFilters,
  toIncidentQuery,
} from "./incidents";

describe("incident filters ↔ URL", () => {
  it("round-trips every filter through the query string", () => {
    const filters = {
      view: "resolved" as const,
      severities: ["CRITICAL" as const, "HIGH" as const],
      serviceId: 3,
      assignee: "unassigned" as const,
      q: "DEVICE-108",
      sort: "severity" as const,
      page: 2,
    };
    const params = serializeIncidentFilters(filters);
    expect(params.toString()).toBe("view=resolved&severity=CRITICAL%2CHIGH&service=3&assignee=unassigned&q=DEVICE-108&sort=severity&page=2");
    expect(parseIncidentFilters(params)).toEqual(filters);
  });

  it("omits defaults so shared links stay short", () => {
    expect(serializeIncidentFilters(DEFAULT_FILTERS).toString()).toBe("");
  });

  it("falls back to defaults for malformed values instead of failing", () => {
    const parsed = parseIncidentFilters(new URLSearchParams("view=bogus&sort=loudest&page=-4&service=abc&severity=critical,NOPE"));
    expect(parsed).toEqual({ ...DEFAULT_FILTERS, severities: ["CRITICAL"] });
  });

  it("keeps severities in a stable, most-severe-first order", () => {
    expect(parseIncidentFilters(new URLSearchParams("severity=LOW,CRITICAL")).severities).toEqual(["CRITICAL", "LOW"]);
  });

  it("maps filters to the API query", () => {
    expect(toIncidentQuery({ ...DEFAULT_FILTERS, assignee: "me", page: 3 }, 7, 20)).toEqual({
      status: ["OPEN", "INVESTIGATING"],
      severity: undefined,
      serviceId: undefined,
      assigneeId: 7,
      unassigned: undefined,
      q: undefined,
      sort: "newest",
      page: 2,
      size: 20,
    });
    expect(toIncidentQuery({ ...DEFAULT_FILTERS, view: "all", assignee: "unassigned" }, 7)).toMatchObject({
      status: undefined,
      unassigned: true,
      assigneeId: undefined,
    });
  });

  it("knows when the user has narrowed the list", () => {
    expect(hasActiveFilters(DEFAULT_FILTERS)).toBe(false);
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, view: "resolved" })).toBe(false);
    expect(hasActiveFilters({ ...DEFAULT_FILTERS, q: "gateway" })).toBe(true);
  });
});

describe("buildActivity", () => {
  it("merges history and comments chronologically, showing comments in place of 'added a comment'", () => {
    const history = [
      makeHistory({ id: 1, action: "CREATED", createdAt: "2026-10-08T09:41:18Z" }),
      makeHistory({ id: 2, action: "ASSIGNED", message: "Assigned to Aravind B", createdAt: "2026-10-08T09:42:02Z" }),
      makeHistory({ id: 3, action: "COMMENT_ADDED", message: "Aravind B added a comment", createdAt: "2026-10-08T09:46:31Z" }),
      makeHistory({ id: 4, action: "STATUS_CHANGED", toValue: "RESOLVED", createdAt: "2026-10-08T09:50:12Z" }),
    ];
    const comments = [makeComment({ id: 9, createdAt: "2026-10-08T09:46:31Z" })];

    const activity = buildActivity(history, comments);

    expect(activity.map((item) => item.id)).toEqual(["h-1", "h-2", "c-9", "h-4"]);
    expect(activity.some((item) => item.kind === "history" && item.entry.action === "COMMENT_ADDED")).toBe(false);
  });

  it("orders history before a comment written at the same instant", () => {
    const at = "2026-10-08T10:00:00Z";
    const activity = buildActivity([makeHistory({ id: 5, createdAt: at })], [makeComment({ id: 1, createdAt: at })]);
    expect(activity.map((item) => item.kind)).toEqual(["history", "comment"]);
  });
});
