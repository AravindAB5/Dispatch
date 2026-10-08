package com.dispatch.rule.dto;

import java.math.BigDecimal;

import com.dispatch.event.EventSeverity;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.rule.ComparisonOperator;
import com.dispatch.rule.RuleConditionType;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Full rule definition. Which condition fields are required depends on {@code conditionType}. */
public record RuleRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull RuleConditionType conditionType,
        @Size(max = 64) @Pattern(regexp = EVENT_TYPE_PATTERN, message = EVENT_TYPE_MESSAGE) String eventType,
        @Size(max = 64) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.]*$",
                message = "must be a metadata key such as cpu or disk.root") String metricKey,
        ComparisonOperator operator,
        @Digits(integer = 14, fraction = 4) BigDecimal threshold,
        EventSeverity minEventSeverity,
        Long serviceId,
        @NotNull IncidentSeverity incidentSeverity,
        @Size(max = 200) String titleTemplate,
        @Size(max = 64) @Pattern(regexp = EVENT_TYPE_PATTERN, message = EVENT_TYPE_MESSAGE) String recoveryEventType,
        Boolean autoResolve,
        Boolean enabled) {

    static final String EVENT_TYPE_PATTERN = "^[A-Z][A-Z0-9_]*$";
    static final String EVENT_TYPE_MESSAGE = "must be UPPER_SNAKE_CASE, e.g. DEVICE_OFFLINE";
}
