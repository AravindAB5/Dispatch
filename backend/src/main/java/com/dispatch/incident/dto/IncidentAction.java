package com.dispatch.incident.dto;

import com.dispatch.incident.Incident;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.incident.IncidentStatus;
import com.dispatch.rule.AlertRule;

/**
 * What alert processing did with an event. Returned to the event producer so the effect of an
 * ingestion request is observable (useful for integrations, tests and demos).
 */
public record IncidentAction(
        Type type,
        Long incidentId,
        String key,
        String title,
        IncidentSeverity severity,
        IncidentStatus status,
        Long ruleId,
        String ruleName,
        Long serviceId,
        Long deviceId) {

    public enum Type {
        /** A new incident was opened. */
        CREATED,
        /** The event repeated an ongoing problem and was attached to the existing incident. */
        DEDUPLICATED,
        /** The problem came back after a recovery signal on a still-open incident. */
        RECURRED,
        /** A recovery signal was recorded but the incident was left open for an operator. */
        RECOVERY_DETECTED,
        /** A recovery signal resolved the incident automatically. */
        AUTO_RESOLVED
    }

    public static IncidentAction of(Type type, Incident incident, AlertRule rule) {
        return new IncidentAction(type, incident.getId(), incident.getKey(), incident.getTitle(),
                incident.getSeverity(), incident.getStatus(), rule.getId(), rule.getName(),
                incident.getService() == null ? null : incident.getService().getId(),
                incident.getDevice() == null ? null : incident.getDevice().getId());
    }
}
