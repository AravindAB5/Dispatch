import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { jsonResponse } from "@/test/fixtures";
import { renderWithQuery } from "@/test/render";

const replace = vi.fn();
let search = new URLSearchParams();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
  useSearchParams: () => search,
}));

import { LoginForm, safeNextPath } from "./login-form";

describe("safeNextPath", () => {
  const origin = "http://localhost:3000";
  it.each([
    [null, "/dashboard"],
    ["/incidents/41", "/incidents/41"],
    ["/incidents?view=resolved#top", "/incidents?view=resolved#top"],
    ["https://evil.example", "/dashboard"],
    ["//evil.example/path", "/dashboard"],
    ["/\\evil.example", "/dashboard"],
    ["/\t/evil.example", "/dashboard"],
    ["/login?next=/x", "/dashboard"],
  ])("%j → %s", (next, expected) => {
    expect(safeNextPath(next, origin)).toBe(expected);
  });
});

describe("LoginForm", () => {
  beforeEach(() => {
    replace.mockClear();
    search = new URLSearchParams();
    document.cookie = "dispatch_session=; Max-Age=0; Path=/";
  });

  it("shows the API's specific message when credentials are wrong", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(401, { title: "Unauthorized", status: 401, detail: "Invalid email or password", code: "INVALID_CREDENTIALS" }),
    );
    const user = userEvent.setup();
    renderWithQuery(<LoginForm />);

    await user.type(screen.getByLabelText("Email"), "operator@dispatch.local");
    await user.type(screen.getByLabelText("Password"), "nope");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password");
    expect(replace).not.toHaveBeenCalled();
  });

  it("stores the session and returns the user to where they were", async () => {
    search = new URLSearchParams("next=/incidents/41");
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(200, {
        accessToken: "header.payload.signature",
        tokenType: "Bearer",
        expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
        user: { id: 2, name: "Aravind B", email: "operator@dispatch.local", role: "OPERATOR" },
      }),
    );
    const user = userEvent.setup();
    renderWithQuery(<LoginForm />);

    await user.click(screen.getByRole("button", { name: "Use the Operator demo account" }));
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/incidents/41"));
    expect(document.cookie).toContain("dispatch_session=header.payload.signature");
  });

  it("explains why the user was signed out", () => {
    search = new URLSearchParams("reason=expired");
    renderWithQuery(<LoginForm />);
    expect(screen.getByText(/session expired/i)).toBeInTheDocument();
  });
});
