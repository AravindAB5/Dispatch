package com.dispatch.incident.dto;

import java.time.Instant;

import com.dispatch.device.dto.DeviceRef;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.incident.IncidentStatus;
import com.dispatch.rule.dto.RuleRef;
import com.dispatch.service.dto.ServiceRef;
import com.dispatch.user.dto.UserSummary;

public record IncidentDetail(
        Long id,
        String key,
        String title,
        String description,
        IncidentSeverity severity,
        IncidentStatus status,
        ServiceRef service,
        DeviceRef device,
        UserSummary assignee,
        UserSummary createdBy,
        RuleRef rule,
        boolean automated,
        String fingerprint,
        Long sourceEventId,
        int eventCount,
        Instant lastEventAt,
        Instant acknowledgedAt,
        Instant recoveredAt,
        Instant resolvedAt,
        String resolutionNote,
        Instant createdAt,
        Instant updatedAt) {
}
