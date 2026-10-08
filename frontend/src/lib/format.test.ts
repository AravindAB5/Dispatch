import { describe, expect, it } from "vitest";

import { decodeClaims, isExpired } from "./auth/jwt";
import { formatDuration, initials, timeAgo } from "./format";

const now = Date.parse("2026-10-08T10:00:00Z");

describe("timeAgo", () => {
  it.each([
    ["2026-10-08T09:59:58Z", "just now"],
    ["2026-10-08T09:59:18Z", "42s ago"],
    ["2026-10-08T09:48:00Z", "12m ago"],
    ["2026-10-08T07:00:00Z", "3h ago"],
    ["2026-10-06T10:00:00Z", "2d ago"],
  ])("%s → %s", (iso, expected) => {
    expect(timeAgo(iso, now)).toBe(expected);
  });

  it("renders a dash for missing values", () => {
    expect(timeAgo(null, now)).toBe("—");
  });
});

describe("formatDuration", () => {
  it.each([
    [45_000, "45s"],
    [12 * 60_000, "12m"],
    [2 * 3_600_000 + 5 * 60_000, "2h 05m"],
    [3 * 86_400_000 + 4 * 3_600_000, "3d 4h"],
    [null, "—"],
  ])("%s ms → %s", (ms, expected) => {
    expect(formatDuration(ms)).toBe(expected);
  });
});

describe("initials", () => {
  it("uses the first two words", () => {
    expect(initials("Priya Raman")).toBe("PR");
    expect(initials("Aravind")).toBe("A");
  });
});

describe("JWT helpers", () => {
  const token = (payload: object) =>
    `x.${btoa(JSON.stringify(payload)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")}.sig`;

  it("reads claims without verifying (UI hints only)", () => {
    expect(decodeClaims(token({ sub: "2", roles: ["OPERATOR"] }))).toMatchObject({ sub: "2", roles: ["OPERATOR"] });
    expect(decodeClaims("not-a-jwt")).toBeNull();
  });

  it("treats tokens close to expiry as expired", () => {
    expect(isExpired(token({ exp: now / 1000 + 3600 }), now)).toBe(false);
    expect(isExpired(token({ exp: now / 1000 + 5 }), now)).toBe(true);
    expect(isExpired(token({}), now)).toBe(true);
    expect(isExpired(undefined, now)).toBe(true);
  });
});
