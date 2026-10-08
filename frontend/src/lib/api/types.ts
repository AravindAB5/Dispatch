/* Types mirroring the Dispatch API contract (backend `dto` packages). */

export type Role = "ADMIN" | "OPERATOR";
export type IncidentSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type IncidentStatus = "OPEN" | "INVESTIGATING" | "RESOLVED";
export type EventSeverity = "INFO" | IncidentSeverity;
export type SourceType = "DEVICE" | "SERVICE" | "EXTERNAL";
export type ServiceStatus = "HEALTHY" | "DEGRADED" | "DOWN";
export type DeviceStatus = "ONLINE" | "DEGRADED" | "OFFLINE";
export type Environment = "PRODUCTION" | "STAGING" | "DEVELOPMENT";
export type RuleConditionType = "EVENT_TYPE" | "METRIC_THRESHOLD" | "SEVERITY_AT_LEAST";
export type ComparisonOperator = "GT" | "GTE" | "LT" | "LTE";
export type IncidentEventRelation = "TRIGGER" | "OCCURRENCE" | "RECOVERY";
export type HistoryAction =
  | "CREATED"
  | "STATUS_CHANGED"
  | "SEVERITY_CHANGED"
  | "ASSIGNED"
  | "UNASSIGNED"
  | "COMMENT_ADDED"
  | "RECOVERY_DETECTED"
  | "RECURRED"
  | "AUTO_RESOLVED"
  | "REOPENED";

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type CursorPage<T> = {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
};

// ---- auth & users ----------------------------------------------------------------------

export type AuthUser = { id: number; name: string; email: string; role: Role };

export type LoginResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresAt: string;
  user: AuthUser;
};

export type UserSummary = { id: number; name: string; email: string };

export type User = {
  id: number;
  name: string;
  email: string;
  role: Role;
  active: boolean;
  activeIncidents: number;
  createdAt: string;
};

// ---- services & devices ----------------------------------------------------------------

export type ServiceRef = { id: number; name: string; status: ServiceStatus };
export type DeviceRef = { id: number; identifier: string; name: string; status: DeviceStatus };

export type Service = {
  id: number;
  name: string;
  description: string | null;
  environment: Environment;
  ownerTeam: string | null;
  status: ServiceStatus;
  statusChangedAt: string;
  devices: { total: number; online: number; degraded: number; offline: number };
  activeIncidents: { total: number; critical: number; high: number; medium: number; low: number };
  createdAt: string;
  updatedAt: string;
};

export type Device = {
  id: number;
  identifier: string;
  name: string;
  deviceType: string;
  location: string | null;
  status: DeviceStatus;
  lastSeenAt: string | null;
  service: ServiceRef;
  activeIncidents: number;
  createdAt: string;
  updatedAt: string;
};

// ---- events ----------------------------------------------------------------------------

export type EventIncidentLink = {
  id: number;
  key: string;
  title: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  relation: IncidentEventRelation;
};

export type DispatchEvent = {
  id: number;
  eventType: string;
  severity: EventSeverity;
  sourceType: SourceType;
  sourceId: string;
  service: ServiceRef | null;
  device: DeviceRef | null;
  message: string;
  metadata: Record<string, unknown>;
  occurredAt: string;
  receivedAt: string;
  incidents: EventIncidentLink[];
};

export type IncidentActionType = "CREATED" | "DEDUPLICATED" | "RECURRED" | "RECOVERY_DETECTED" | "AUTO_RESOLVED";

export type IncidentAction = {
  type: IncidentActionType;
  incidentId: number;
  key: string;
  title: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  ruleId: number;
  ruleName: string;
  serviceId: number | null;
  deviceId: number | null;
};

export type IngestionResponse = { event: DispatchEvent; incidentActions: IncidentAction[] };

export type EventIngestRequest = {
  eventType: string;
  severity: EventSeverity;
  sourceType: SourceType;
  sourceId: string;
  serviceId?: number | null;
  message: string;
  metadata?: Record<string, unknown>;
  timestamp?: string;
};

// ---- incidents -------------------------------------------------------------------------

export type IncidentSummary = {
  id: number;
  key: string;
  title: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  service: ServiceRef | null;
  device: DeviceRef | null;
  assignee: UserSummary | null;
  automated: boolean;
  eventCount: number;
  lastEventAt: string | null;
  acknowledgedAt: string | null;
  recoveredAt: string | null;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type RuleRef = {
  id: number;
  name: string;
  condition: string;
  recoveryEventType: string | null;
  autoResolve: boolean;
};

export type IncidentDetail = IncidentSummary & {
  description: string | null;
  createdBy: UserSummary | null;
  rule: RuleRef | null;
  fingerprint: string | null;
  sourceEventId: number | null;
  resolutionNote: string | null;
};

export type HistoryEntry = {
  id: number;
  action: HistoryAction;
  fromValue: string | null;
  toValue: string | null;
  message: string;
  actor: UserSummary | null;
  createdAt: string;
};

export type Comment = {
  id: number;
  incidentId: number;
  body: string;
  author: UserSummary;
  createdAt: string;
};

export type RelatedEvents = {
  total: number;
  triggers: number;
  occurrences: number;
  recoveries: number;
  items: { relation: IncidentEventRelation; linkedAt: string; event: DispatchEvent }[];
};

// ---- rules -----------------------------------------------------------------------------

export type Rule = {
  id: number;
  name: string;
  description: string | null;
  conditionType: RuleConditionType;
  eventType: string | null;
  metricKey: string | null;
  operator: ComparisonOperator | null;
  threshold: number | null;
  minEventSeverity: EventSeverity | null;
  service: ServiceRef | null;
  incidentSeverity: IncidentSeverity;
  titleTemplate: string | null;
  recoveryEventType: string | null;
  autoResolve: boolean;
  enabled: boolean;
  condition: string;
  activeIncidents: number;
  totalIncidents: number;
  createdAt: string;
  updatedAt: string;
};

export type RuleRequest = {
  name: string;
  description?: string | null;
  conditionType: RuleConditionType;
  eventType?: string | null;
  metricKey?: string | null;
  operator?: ComparisonOperator | null;
  threshold?: number | null;
  minEventSeverity?: EventSeverity | null;
  serviceId?: number | null;
  incidentSeverity: IncidentSeverity;
  titleTemplate?: string | null;
  recoveryEventType?: string | null;
  autoResolve: boolean;
  enabled: boolean;
};

// ---- dashboard -------------------------------------------------------------------------

export type HealthCounts = { total: number; healthy: number; degraded: number; down: number };

export type DashboardSummary = {
  activeIncidents: {
    total: number;
    critical: number;
    high: number;
    medium: number;
    low: number;
    open: number;
    investigating: number;
    unassigned: number;
  };
  last24h: { created: number; resolved: number };
  responseTimes: { mttaSeconds: number | null; mttrSeconds: number | null; windowDays: number };
  events: { lastHour: number; last24h: number };
  services: HealthCounts;
  devices: HealthCounts;
  generatedAt: string;
};

export type TrendPoint = {
  bucketStart: string;
  critical: number;
  high: number;
  medium: number;
  low: number;
  resolved: number;
};

export type VolumePoint = { bucketStart: string; total: number; problems: number };

export type SimulatorStatus = {
  running: boolean;
  startedAt: string | null;
  intervalMs: number;
  eventsGenerated: number;
  maxConcurrentProblems: number;
  activeProblems: string[];
};

// ---- realtime --------------------------------------------------------------------------

export type ServiceStatusChange = {
  id: number;
  name: string;
  status: ServiceStatus;
  previousStatus: ServiceStatus;
  statusChangedAt: string;
};

export type DeviceStatusChange = {
  id: number;
  identifier: string;
  name: string;
  serviceId: number;
  status: DeviceStatus;
  previousStatus: DeviceStatus;
  lastSeenAt: string | null;
};

export type RealtimeMessage =
  | { type: "event.created"; data: DispatchEvent; sentAt: string }
  | { type: "incident.created" | "incident.updated" | "incident.resolved"; data: IncidentSummary; sentAt: string }
  | { type: "service.updated"; data: ServiceStatusChange; sentAt: string }
  | { type: "device.updated"; data: DeviceStatusChange; sentAt: string };
