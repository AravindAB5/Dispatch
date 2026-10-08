import { beforeEach, describe, expect, it, vi } from "vitest";

import { jsonResponse } from "@/test/fixtures";

vi.mock("@/lib/auth/session", () => ({
  getToken: vi.fn(() => "token-123"),
  redirectToLogin: vi.fn(),
}));

import { redirectToLogin } from "@/lib/auth/session";

import { api, toSearchParams } from "./client";
import { ApiError, NetworkError, describeError } from "./errors";

describe("toSearchParams", () => {
  it("repeats array values and skips empty ones", () => {
    const params = toSearchParams({ status: ["OPEN", "INVESTIGATING"], q: "", serviceId: 3, page: 0, missing: undefined });
    expect(params.toString()).toBe("status=OPEN&status=INVESTIGATING&serviceId=3&page=0");
  });
});

describe("api()", () => {
  beforeEach(() => {
    vi.mocked(redirectToLogin).mockClear();
  });

  it("sends the bearer token and JSON body", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(200, { ok: true }));

    await expect(api("/incidents/41/status", { method: "PUT", body: { status: "RESOLVED" } })).resolves.toEqual({ ok: true });

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe("http://localhost:8080/api/v1/incidents/41/status");
    expect(init?.method).toBe("PUT");
    expect((init?.headers as Record<string, string>).Authorization).toBe("Bearer token-123");
    expect(init?.body).toBe(JSON.stringify({ status: "RESOLVED" }));
  });

  it("turns problem details into a typed ApiError with field errors", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(400, {
        title: "Validation failed",
        status: 400,
        detail: "The request has 2 invalid fields",
        code: "VALIDATION_FAILED",
        requestId: "abc12345-0000",
        errors: [
          { field: "email", message: "must be a well-formed email address" },
          { field: "password", message: "must not be blank" },
        ],
      }),
    );

    const error = (await api("/auth/login", { method: "POST", body: {}, skipAuthRedirect: true }).catch((e) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.code).toBe("VALIDATION_FAILED");
    expect(error.fieldErrors).toEqual({ email: "must be a well-formed email address", password: "must not be blank" });
    expect(describeError(error)).toBe("Some fields need attention.");
  });

  it("sends the user to the login page when the session has expired", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(401, { code: "TOKEN_INVALID", detail: "expired" }));

    await expect(api("/incidents")).rejects.toBeInstanceOf(ApiError);
    expect(redirectToLogin).toHaveBeenCalledWith("expired");
  });

  it("does not redirect for the login call itself", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(401, { code: "INVALID_CREDENTIALS", detail: "Invalid email or password" }));

    const error = await api("/auth/login", { method: "POST", skipAuthRedirect: true }).catch((e) => e);

    expect(describeError(error)).toBe("Invalid email or password");
    expect(redirectToLogin).not.toHaveBeenCalled();
  });

  it("reports an unreachable API distinctly from HTTP errors", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));

    const error = await api("/incidents").catch((e) => e);

    expect(error).toBeInstanceOf(NetworkError);
    expect(describeError(error)).toContain("Can't reach the Dispatch API");
  });
});

describe("describeError", () => {
  it("hides server internals but keeps the request id for support", () => {
    const error = new ApiError(500, { detail: "NullPointerException at ...", requestId: "f00dbabe-1234" });
    expect(describeError(error)).toBe("The server hit an unexpected error (request f00dbabe). Try again in a moment.");
  });

  it("uses the API's specific message for business rule failures", () => {
    const error = new ApiError(409, { code: "ACTIVE_DUPLICATE_EXISTS", detail: "Cannot reopen: INC-57 is already tracking this problem" });
    expect(describeError(error)).toBe("Cannot reopen: INC-57 is already tracking this problem");
  });
});
