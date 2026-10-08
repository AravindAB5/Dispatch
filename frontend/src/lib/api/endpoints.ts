import { api, type Query } from "./client";
import type {
  AuthUser,
  Comment,
  CursorPage,
  DashboardSummary,
  Device,
  DispatchEvent,
  EventIngestRequest,
  HistoryEntry,
  IncidentDetail,
  IncidentSeverity,
  IncidentStatus,
  IncidentSummary,
  IngestionResponse,
  LoginResponse,
  Page,
  RelatedEvents,
  Role,
  Rule,
  RuleRequest,
  Service,
  SimulatorStatus,
  TrendPoint,
  User,
  VolumePoint,
} from "./types";

export const authApi = {
  login: (email: string, password: string) =>
    api<LoginResponse>("/auth/login", { method: "POST", body: { email, password }, skipAuthRedirect: true }),
  me: () => api<AuthUser>("/auth/me"),
};

export type IncidentQuery = {
  status?: IncidentStatus[];
  severity?: IncidentSeverity[];
  serviceId?: number;
  deviceId?: number;
  assigneeId?: number;
  unassigned?: boolean;
  q?: string;
  sort?: "newest" | "oldest" | "updated" | "severity";
  page?: number;
  size?: number;
};

export const incidentsApi = {
  list: (query: IncidentQuery, signal?: AbortSignal) =>
    api<Page<IncidentSummary>>("/incidents", { query: query as Query, signal }),
  get: (id: number) => api<IncidentDetail>(`/incidents/${id}`),
  history: (id: number) => api<HistoryEntry[]>(`/incidents/${id}/history`),
  comments: (id: number) => api<Comment[]>(`/incidents/${id}/comments`),
  events: (id: number, limit = 50) => api<RelatedEvents>(`/incidents/${id}/events`, { query: { limit } }),
  declare: (body: {
    title: string;
    description?: string;
    severity: IncidentSeverity;
    serviceId?: number | null;
    deviceId?: number | null;
  }) => api<IncidentDetail>("/incidents", { method: "POST", body }),
  assign: (id: number, assigneeId: number | null) =>
    api<IncidentDetail>(`/incidents/${id}/assignee`, { method: "PUT", body: { assigneeId } }),
  changeStatus: (id: number, status: IncidentStatus, note?: string) =>
    api<IncidentDetail>(`/incidents/${id}/status`, { method: "PUT", body: { status, note } }),
  changeSeverity: (id: number, severity: IncidentSeverity) =>
    api<IncidentDetail>(`/incidents/${id}/severity`, { method: "PUT", body: { severity } }),
  comment: (id: number, body: string) => api<Comment>(`/incidents/${id}/comments`, { method: "POST", body: { body } }),
};

export type EventQuery = {
  type?: string[];
  severity?: string[];
  serviceId?: number;
  deviceId?: number;
  q?: string;
  limit?: number;
};

export const eventsApi = {
  list: (query: EventQuery & { cursor?: string | null }, signal?: AbortSignal) =>
    api<CursorPage<DispatchEvent>>("/events", { query: query as Query, signal }),
  get: (id: number) => api<DispatchEvent>(`/events/${id}`),
  types: () => api<string[]>("/events/types"),
  ingest: (body: EventIngestRequest) => api<IngestionResponse>("/events", { method: "POST", body }),
};

export const servicesApi = {
  list: () => api<Service[]>("/services"),
  get: (id: number) => api<Service>(`/services/${id}`),
  create: (body: ServiceInput) => api<Service>("/services", { method: "POST", body }),
  update: (id: number, body: ServiceInput) => api<Service>(`/services/${id}`, { method: "PUT", body }),
};

export type ServiceInput = {
  name: string;
  description?: string;
  environment: Service["environment"];
  ownerTeam?: string;
};

export type DeviceQuery = { serviceId?: number; status?: string[]; q?: string; page?: number; size?: number };

export type DeviceInput = {
  identifier: string;
  name: string;
  deviceType: string;
  serviceId: number;
  location?: string;
};

export const devicesApi = {
  list: (query: DeviceQuery, signal?: AbortSignal) => api<Page<Device>>("/devices", { query: query as Query, signal }),
  get: (id: number) => api<Device>(`/devices/${id}`),
  create: (body: DeviceInput) => api<Device>("/devices", { method: "POST", body }),
  update: (id: number, body: DeviceInput) => api<Device>(`/devices/${id}`, { method: "PUT", body }),
};

export const rulesApi = {
  list: () => api<Rule[]>("/rules"),
  create: (body: RuleRequest) => api<Rule>("/rules", { method: "POST", body }),
  update: (id: number, body: RuleRequest) => api<Rule>(`/rules/${id}`, { method: "PUT", body }),
  toggle: (id: number, enabled: boolean) => api<Rule>(`/rules/${id}`, { method: "PATCH", body: { enabled } }),
  remove: (id: number) => api<void>(`/rules/${id}`, { method: "DELETE" }),
};

export const usersApi = {
  list: () => api<User[]>("/users"),
  create: (body: { name: string; email: string; password: string; role: Role }) =>
    api<User>("/users", { method: "POST", body }),
  update: (id: number, body: { name?: string; role?: Role; active?: boolean }) =>
    api<User>(`/users/${id}`, { method: "PATCH", body }),
};

export const dashboardApi = {
  summary: () => api<DashboardSummary>("/dashboard/summary"),
  trend: (hours: number, tz: string) => api<TrendPoint[]>("/dashboard/incident-trend", { query: { hours, tz } }),
  volume: (minutes: number) => api<VolumePoint[]>("/dashboard/event-volume", { query: { minutes } }),
};

export const simulatorApi = {
  status: () => api<SimulatorStatus>("/simulator"),
  start: () => api<SimulatorStatus>("/simulator/start", { method: "POST" }),
  stop: () => api<SimulatorStatus>("/simulator/stop", { method: "POST" }),
};
