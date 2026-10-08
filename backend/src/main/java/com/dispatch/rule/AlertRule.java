package com.dispatch.rule;

import java.math.BigDecimal;

import com.dispatch.common.persistence.AuditedEntity;
import com.dispatch.event.EventSeverity;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.service.MonitoredService;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Converts matching events into incidents.
 * <pre>
 *   WHEN  &lt;condition&gt;            (event type / metric threshold / minimum severity)
 *   [FOR  service]                 (optional scope)
 *   THEN  open &lt;severity&gt; incident
 *   UNTIL &lt;recoveryEventType&gt;    (optional; auto-resolves OPEN incidents when autoResolve is set)
 * </pre>
 */
@Entity
@Table(name = "alert_rules")
public class AlertRule extends AuditedEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 32)
    private RuleConditionType conditionType;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "metric_key", length = 64)
    private String metricKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_operator", length = 8)
    private ComparisonOperator operator;

    @Column(precision = 18, scale = 4)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_event_severity", length = 16)
    private EventSeverity minEventSeverity;

    /** Optional scope: when set, the rule only applies to events of this service. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private MonitoredService service;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_severity", nullable = false, length = 16)
    private IncidentSeverity incidentSeverity;

    /** e.g. "{source} is offline". See {@code IncidentTitles} for the supported placeholders. */
    @Column(name = "title_template", length = 200)
    private String titleTemplate;

    @Column(name = "recovery_event_type", length = 64)
    private String recoveryEventType;

    @Column(name = "auto_resolve", nullable = false)
    private boolean autoResolve = true;

    @Column(nullable = false)
    private boolean enabled = true;

    protected AlertRule() {
    }

    private AlertRule(String name, RuleConditionType conditionType, IncidentSeverity incidentSeverity) {
        this.name = name;
        this.conditionType = conditionType;
        this.incidentSeverity = incidentSeverity;
    }

    public static AlertRule onEventType(String name, String eventType, IncidentSeverity severity) {
        AlertRule rule = new AlertRule(name, RuleConditionType.EVENT_TYPE, severity);
        rule.eventType = eventType;
        return rule;
    }

    public static AlertRule onMetric(String name, String eventType, String metricKey, ComparisonOperator operator,
            BigDecimal threshold, IncidentSeverity severity) {
        AlertRule rule = new AlertRule(name, RuleConditionType.METRIC_THRESHOLD, severity);
        rule.eventType = eventType;
        rule.metricKey = metricKey;
        rule.operator = operator;
        rule.threshold = threshold;
        return rule;
    }

    public static AlertRule onSeverity(String name, EventSeverity minimum, IncidentSeverity severity) {
        AlertRule rule = new AlertRule(name, RuleConditionType.SEVERITY_AT_LEAST, severity);
        rule.minEventSeverity = minimum;
        return rule;
    }

    /** Creates an empty rule to be populated by {@link #redefine}. */
    public static AlertRule blank() {
        return new AlertRule();
    }

    // ---- fluent configuration (used by seed data and tests) -------------------------------

    public AlertRule recoveredBy(String recoveryEventType) {
        this.recoveryEventType = recoveryEventType;
        return this;
    }

    public AlertRule autoResolve(boolean autoResolve) {
        this.autoResolve = autoResolve;
        return this;
    }

    public AlertRule titled(String titleTemplate) {
        this.titleTemplate = titleTemplate;
        return this;
    }

    public AlertRule describedAs(String description) {
        this.description = description;
        return this;
    }

    public AlertRule scopedTo(MonitoredService service) {
        this.service = service;
        return this;
    }

    public AlertRule enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /** Replaces the whole definition (used by the admin API). Irrelevant fields are cleared. */
    public void redefine(String name, String description, RuleConditionType conditionType, String eventType,
            String metricKey, ComparisonOperator operator, BigDecimal threshold, EventSeverity minEventSeverity,
            MonitoredService service, IncidentSeverity incidentSeverity, String titleTemplate,
            String recoveryEventType, boolean autoResolve, boolean enabled) {
        this.name = name;
        this.description = description;
        this.conditionType = conditionType;
        boolean metric = conditionType == RuleConditionType.METRIC_THRESHOLD;
        this.eventType = eventType;
        this.metricKey = metric ? metricKey : null;
        this.operator = metric ? operator : null;
        this.threshold = metric ? threshold : null;
        this.minEventSeverity = conditionType == RuleConditionType.SEVERITY_AT_LEAST ? minEventSeverity : null;
        this.service = service;
        this.incidentSeverity = incidentSeverity;
        this.titleTemplate = titleTemplate;
        this.recoveryEventType = recoveryEventType;
        this.autoResolve = autoResolve;
        this.enabled = enabled;
    }

    /** Human-readable condition, e.g. {@code cpu > 90 on CPU_USAGE_HIGH}. */
    public String conditionSummary() {
        return switch (conditionType) {
            case EVENT_TYPE -> eventType + " received";
            case METRIC_THRESHOLD -> "%s %s %s on %s".formatted(metricKey, operator.symbol(),
                    threshold.stripTrailingZeros().toPlainString(), eventType);
            case SEVERITY_AT_LEAST -> (eventType == null ? "Any event" : eventType) + " with severity >= "
                    + minEventSeverity;
        };
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public RuleConditionType getConditionType() {
        return conditionType;
    }

    public String getEventType() {
        return eventType;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public ComparisonOperator getOperator() {
        return operator;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public EventSeverity getMinEventSeverity() {
        return minEventSeverity;
    }

    public MonitoredService getService() {
        return service;
    }

    public IncidentSeverity getIncidentSeverity() {
        return incidentSeverity;
    }

    public String getTitleTemplate() {
        return titleTemplate;
    }

    public String getRecoveryEventType() {
        return recoveryEventType;
    }

    public boolean isAutoResolve() {
        return autoResolve;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
