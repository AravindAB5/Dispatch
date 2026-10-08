import type {
  DeviceStatus,
  EventSeverity,
  IncidentSeverity,
  IncidentStatus,
  RuleConditionType,
  ServiceStatus,
} from "@/lib/api/types";

export const SEVERITIES: IncidentSeverity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];
export const EVENT_SEVERITIES: EventSeverity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"];
export const STATUSES: IncidentStatus[] = ["OPEN", "INVESTIGATING", "RESOLVED"];

export const SEVERITY_RANK: Record<EventSeverity, number> = { INFO: 0, LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 };

export const SEVERITY_LABEL: Record<EventSeverity, string> = {
  CRITICAL: "Critical",
  HIGH: "High",
  MEDIUM: "Medium",
  LOW: "Low",
  INFO: "Info",
};

export const STATUS_LABEL: Record<IncidentStatus, string> = {
  OPEN: "Open",
  INVESTIGATING: "Investigating",
  RESOLVED: "Resolved",
};

export const SERVICE_STATUS_LABEL: Record<ServiceStatus, string> = {
  HEALTHY: "Healthy",
  DEGRADED: "Degraded",
  DOWN: "Down",
};

export const DEVICE_STATUS_LABEL: Record<DeviceStatus, string> = {
  ONLINE: "Online",
  DEGRADED: "Degraded",
  OFFLINE: "Offline",
};

export const CONDITION_LABEL: Record<RuleConditionType, string> = {
  EVENT_TYPE: "Event type",
  METRIC_THRESHOLD: "Metric threshold",
  SEVERITY_AT_LEAST: "Minimum severity",
};

export const OPERATOR_SYMBOL = { GT: ">", GTE: "≥", LT: "<", LTE: "≤" } as const;

export const TITLE_PLACEHOLDERS = ["source", "device", "service", "rule", "eventType", "metric", "value"];
