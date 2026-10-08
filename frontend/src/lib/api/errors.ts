import { API_URL } from "@/lib/config";

/** Mirrors the RFC 9457 problem details the API returns, plus Dispatch's extension members. */
export type ProblemDetails = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  requestId?: string;
  errors?: { field: string; message: string }[];
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly title: string;
  readonly detail: string;
  readonly requestId?: string;
  /** field name → message, for highlighting form inputs */
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, problem: ProblemDetails) {
    const detail = problem.detail ?? problem.title ?? `Request failed with status ${status}`;
    super(detail);
    this.name = "ApiError";
    this.status = status;
    this.code = problem.code ?? `HTTP_${status}`;
    this.title = problem.title ?? "Request failed";
    this.detail = detail;
    this.requestId = problem.requestId;
    this.fieldErrors = Object.fromEntries((problem.errors ?? []).map((e) => [e.field, e.message]));
  }

  static async fromResponse(response: Response): Promise<ApiError> {
    let problem: ProblemDetails = {};
    try {
      const text = await response.text();
      problem = text ? (JSON.parse(text) as ProblemDetails) : {};
    } catch {
      problem = {};
    }
    return new ApiError(response.status, problem);
  }
}

/** The network itself failed (API down, CORS, offline) — distinct from an HTTP error. */
export class NetworkError extends Error {
  constructor() {
    super(`Can't reach the Dispatch API at ${API_URL}. Check that the backend is running.`);
    this.name = "NetworkError";
  }
}

/** A specific, human-readable message for any thrown value. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status >= 500) {
      return `The server hit an unexpected error${error.requestId ? ` (request ${error.requestId.slice(0, 8)})` : ""}. Try again in a moment.`;
    }
    if (error.code === "VALIDATION_FAILED" && Object.keys(error.fieldErrors).length > 1) {
      return "Some fields need attention.";
    }
    return error.detail;
  }
  if (error instanceof NetworkError) return error.message;
  if (error instanceof Error) return error.message;
  return "Unexpected error";
}
