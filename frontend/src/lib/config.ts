/** Address the browser uses to reach the API. Baked in at build time (NEXT_PUBLIC_*). */
export const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").replace(/\/$/, "");

/** STOMP-over-WebSocket endpoint, derived from the API address. */
export const WS_URL = `${API_URL.replace(/^http/, "ws")}/ws`;

export const SHOW_DEMO_CREDENTIALS = process.env.NEXT_PUBLIC_SHOW_DEMO_CREDENTIALS === "true";

export const SESSION_COOKIE = "dispatch_session";
