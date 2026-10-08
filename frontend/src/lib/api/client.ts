import { getToken, redirectToLogin } from "@/lib/auth/session";
import { API_URL } from "@/lib/config";
import { ApiError, NetworkError } from "./errors";

export type QueryValue = string | number | boolean | null | undefined | (string | number)[];
export type Query = Record<string, QueryValue>;

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  query?: Query;
  signal?: AbortSignal;
  /** Skip the global "session expired → login" redirect (used by the login call itself). */
  skipAuthRedirect?: boolean;
};

/** Serialises a query object; arrays become repeated keys (`?status=OPEN&status=INVESTIGATING`). */
export function toSearchParams(query: Query = {}): URLSearchParams {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === "") continue;
    if (Array.isArray(value)) {
      value.forEach((v) => params.append(key, String(v)));
    } else {
      params.set(key, String(value));
    }
  }
  return params;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, query, signal, skipAuthRedirect } = options;
  const params = toSearchParams(query);
  const url = `${API_URL}/api/v1${path}${params.size ? `?${params}` : ""}`;

  const headers: Record<string, string> = { Accept: "application/json" };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    throw new NetworkError();
  }

  if (!response.ok) {
    const error = await ApiError.fromResponse(response);
    if (response.status === 401 && !skipAuthRedirect && typeof window !== "undefined") {
      redirectToLogin("expired");
    }
    throw error;
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
