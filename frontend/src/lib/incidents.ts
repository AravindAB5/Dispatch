import type { IncidentQuery } from "@/lib/api/endpoints";
import type { Comment, HistoryEntry, IncidentSeverity, IncidentStatus } from "@/lib/api/types";
import { SEVERITIES } from "@/lib/domain";

export type IncidentView = "active" | "open" | "investigating" | "resolved" | "all";
export type IncidentSort = NonNullable<IncidentQuery["sort"]>;
export type AssigneeFilter = "me" | "unassigned" | number | null;

export type IncidentFilters = {
  view: IncidentView;
  severities: IncidentSeverity[];
  serviceId: number | null;
  assignee: AssigneeFilter;
  q: string;
  sort: IncidentSort;
  page: number;
};

export const DEFAULT_FILTERS: IncidentFilters = {
  view: "active",
  severities: [],
  serviceId: null,
  assignee: null,
  q: "",
  sort: "newest",
  page: 1,
};

const VIEWS: IncidentView[] = ["active", "open", "investigating", "resolved", "all"];
const SORTS: IncidentSort[] = ["newest", "oldest", "updated", "severity"];

const VIEW_STATUSES: Record<IncidentView, IncidentStatus[]> = {
  active: ["OPEN", "INVESTIGATING"],
  open: ["OPEN"],
  investigating: ["INVESTIGATING"],
  resolved: ["RESOLVED"],
  all: [],
};

function positiveInt(value: string | null): number | null {
  if (!value) return null;
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : null;
}

/** URL → filters. Unknown or malformed values fall back to defaults instead of erroring. */
export function parseIncidentFilters(params: URLSearchParams): IncidentFilters {
  const view = params.get("view") as IncidentView | null;
  const sort = params.get("sort") as IncidentSort | null;
  const assigneeParam = params.get("assignee");
  const assignee: AssigneeFilter =
    assigneeParam === "me" || assigneeParam === "unassigned" ? assigneeParam : positiveInt(assigneeParam);
  const severities = (params.get("severity") ?? "")
    .split(",")
    .map((s) => s.trim().toUpperCase())
    .filter((s): s is IncidentSeverity => (SEVERITIES as string[]).includes(s));

  return {
    view: view && VIEWS.includes(view) ? view : DEFAULT_FILTERS.view,
    severities: SEVERITIES.filter((s) => severities.includes(s)),
    serviceId: positiveInt(params.get("service")),
    assignee,
    q: params.get("q")?.trim() ?? "",
    sort: sort && SORTS.includes(sort) ? sort : DEFAULT_FILTERS.sort,
    page: positiveInt(params.get("page")) ?? 1,
  };
}

/** Filters → URL, omitting defaults so shared links stay short. */
export function serializeIncidentFilters(filters: IncidentFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.view !== DEFAULT_FILTERS.view) params.set("view", filters.view);
  if (filters.severities.length) params.set("severity", filters.severities.join(","));
  if (filters.serviceId) params.set("service", String(filters.serviceId));
  if (filters.assignee !== null) params.set("assignee", String(filters.assignee));
  if (filters.q) params.set("q", filters.q);
  if (filters.sort !== DEFAULT_FILTERS.sort) params.set("sort", filters.sort);
  if (filters.page > 1) params.set("page", String(filters.page));
  return params;
}

export function toIncidentQuery(filters: IncidentFilters, currentUserId: number | undefined, size = 20): IncidentQuery {
  return {
    status: VIEW_STATUSES[filters.view].length ? VIEW_STATUSES[filters.view] : undefined,
    severity: filters.severities.length ? filters.severities : undefined,
    serviceId: filters.serviceId ?? undefined,
    assigneeId:
      filters.assignee === "me" ? currentUserId : typeof filters.assignee === "number" ? filters.assignee : undefined,
    unassigned: filters.assignee === "unassigned" ? true : undefined,
    q: filters.q || undefined,
    sort: filters.sort,
    page: filters.page - 1,
    size,
  };
}

export function hasActiveFilters(filters: IncidentFilters): boolean {
  return (
    filters.severities.length > 0 || filters.serviceId !== null || filters.assignee !== null || filters.q !== ""
  );
}

// ---- activity timeline -----------------------------------------------------------------

export type ActivityItem =
  | { kind: "history"; id: string; at: string; entry: HistoryEntry }
  | { kind: "comment"; id: string; at: string; comment: Comment };

/**
 * One chronological activity feed: audit history plus comment bodies. The history's
 * "added a comment" entries are dropped because the comment itself is shown in their place.
 */
export function buildActivity(history: HistoryEntry[], comments: Comment[]): ActivityItem[] {
  const items: ActivityItem[] = [
    ...history
      .filter((entry) => entry.action !== "COMMENT_ADDED")
      .map((entry) => ({ kind: "history" as const, id: `h-${entry.id}`, at: entry.createdAt, entry })),
    ...comments.map((comment) => ({ kind: "comment" as const, id: `c-${comment.id}`, at: comment.createdAt, comment })),
  ];
  return items.sort((a, b) => {
    const diff = new Date(a.at).getTime() - new Date(b.at).getTime();
    if (diff !== 0) return diff;
    if (a.kind !== b.kind) return a.kind === "history" ? -1 : 1;
    return a.id.localeCompare(b.id, undefined, { numeric: true });
  });
}
