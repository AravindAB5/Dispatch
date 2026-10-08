package com.dispatch.incident;

import com.dispatch.device.dto.DeviceRef;
import com.dispatch.incident.dto.IncidentDetail;
import com.dispatch.incident.dto.IncidentSummary;
import com.dispatch.rule.dto.RuleRef;
import com.dispatch.service.dto.ServiceRef;
import com.dispatch.user.dto.UserSummary;

import org.springframework.stereotype.Component;

@Component
public class IncidentMapper {

    public IncidentSummary toSummary(Incident i) {
        return new IncidentSummary(
                i.getId(),
                i.getKey(),
                i.getTitle(),
                i.getSeverity(),
                i.getStatus(),
                ServiceRef.from(i.getService()),
                DeviceRef.from(i.getDevice()),
                UserSummary.from(i.getAssignee()),
                i.isAutomated(),
                i.getEventCount(),
                i.getLastEventAt(),
                i.getAcknowledgedAt(),
                i.getRecoveredAt(),
                i.getResolvedAt(),
                i.getCreatedAt(),
                i.getUpdatedAt());
    }

    public IncidentDetail toDetail(Incident i) {
        return new IncidentDetail(
                i.getId(),
                i.getKey(),
                i.getTitle(),
                i.getDescription(),
                i.getSeverity(),
                i.getStatus(),
                ServiceRef.from(i.getService()),
                DeviceRef.from(i.getDevice()),
                UserSummary.from(i.getAssignee()),
                UserSummary.from(i.getCreatedBy()),
                RuleRef.from(i.getRule()),
                i.isAutomated(),
                i.getFingerprint(),
                i.getSourceEvent() == null ? null : i.getSourceEvent().getId(),
                i.getEventCount(),
                i.getLastEventAt(),
                i.getAcknowledgedAt(),
                i.getRecoveredAt(),
                i.getResolvedAt(),
                i.getResolutionNote(),
                i.getCreatedAt(),
                i.getUpdatedAt());
    }
}
