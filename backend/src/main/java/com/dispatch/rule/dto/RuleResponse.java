package com.dispatch.rule.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.dispatch.event.EventSeverity;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.rule.AlertRule;
import com.dispatch.rule.ComparisonOperator;
import com.dispatch.rule.RuleConditionType;
import com.dispatch.service.dto.ServiceRef;

public record RuleResponse(
        Long id,
        String name,
        String description,
        RuleConditionType conditionType,
        String eventType,
        String metricKey,
        ComparisonOperator operator,
        BigDecimal threshold,
        EventSeverity minEventSeverity,
        ServiceRef service,
        IncidentSeverity incidentSeverity,
        String titleTemplate,
        String recoveryEventType,
        boolean autoResolve,
        boolean enabled,
        String condition,
        long activeIncidents,
        long totalIncidents,
        Instant createdAt,
        Instant updatedAt) {

    public static RuleResponse from(AlertRule r, long activeIncidents, long totalIncidents) {
        return new RuleResponse(r.getId(), r.getName(), r.getDescription(), r.getConditionType(), r.getEventType(),
                r.getMetricKey(), r.getOperator(),
                r.getThreshold() == null ? null : r.getThreshold().stripTrailingZeros(),
                r.getMinEventSeverity(), ServiceRef.from(r.getService()), r.getIncidentSeverity(),
                r.getTitleTemplate(), r.getRecoveryEventType(), r.isAutoResolve(), r.isEnabled(),
                r.conditionSummary(), activeIncidents, totalIncidents, r.getCreatedAt(), r.getUpdatedAt());
    }
}
