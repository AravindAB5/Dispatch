package com.dispatch.incident.dto;

import java.time.Instant;

import com.dispatch.device.dto.DeviceRef;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.incident.IncidentStatus;
import com.dispatch.service.dto.ServiceRef;
import com.dispatch.user.dto.UserSummary;

/** Row representation used by incident lists, the dashboard and WebSocket notifications. */
public record IncidentSummary(
        Long id,
        String key,
        String title,
        IncidentSeverity severity,
        IncidentStatus status,
        ServiceRef service,
        DeviceRef device,
        UserSummary assignee,
        boolean automated,
        int eventCount,
        Instant lastEventAt,
        Instant acknowledgedAt,
        Instant recoveredAt,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt) {
}
