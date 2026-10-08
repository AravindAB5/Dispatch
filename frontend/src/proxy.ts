import { NextResponse, type NextRequest } from "next/server";

import { isExpired } from "@/lib/auth/jwt";
import { SESSION_COOKIE } from "@/lib/config";

/**
 * Optimistic route protection: signed-out (or expired) visitors are redirected to /login before any
 * console page renders, and signed-in users skip the login page. This only inspects the cookie; the
 * API remains the authority and rejects any request whose token is invalid.
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const token = request.cookies.get(SESSION_COOKIE)?.value;
  const signedIn = Boolean(token) && !isExpired(token);

  if (pathname === "/login") {
    if (signedIn) return NextResponse.redirect(new URL("/dashboard", request.url));
    return NextResponse.next();
  }

  if (!signedIn) {
    const login = new URL("/login", request.url);
    login.searchParams.set("next", pathname + search);
    if (token) login.searchParams.set("reason", "expired");
    const response = NextResponse.redirect(login);
    if (token) response.cookies.delete(SESSION_COOKIE);
    return response;
  }
  return NextResponse.next();
}

export const config = {
  // Everything except Next.js internals and static files.
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|ico|webp)$).*)"],
};
