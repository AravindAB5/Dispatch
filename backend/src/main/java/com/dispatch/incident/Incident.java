package com.dispatch.incident;

import java.time.Instant;

import com.dispatch.common.error.BusinessRuleException;
import com.dispatch.common.persistence.AuditedEntity;
import com.dispatch.device.Device;
import com.dispatch.event.Event;
import com.dispatch.rule.AlertRule;
import com.dispatch.service.MonitoredService;
import com.dispatch.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * An actionable problem. State changes go through intention-revealing methods that enforce the
 * lifecycle rules, so the services orchestrating them stay thin.
 */
@Entity
@Table(name = "incidents")
public class Incident extends AuditedEntity {

    public static final String KEY_PREFIX = "INC-";

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentSeverity severity;

    /** Database-generated rank used for severity sorting. Read-only. */
    @Column(name = "severity_rank", insertable = false, updatable = false)
    private Integer severityRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Column(length = 255)
    private String fingerprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private AlertRule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_event_id")
    private Event sourceEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private MonitoredService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /** Set when a recovery signal arrived but the incident was intentionally left open. */
    @Column(name = "recovered_at")
    private Instant recoveredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Version
    private Long version;

    protected Incident() {
    }

    /** Opened automatically by an alert rule. */
    public static Incident fromRule(AlertRule rule, Event trigger, String title, String fingerprint) {
        Incident incident = new Incident();
        incident.title = title;
        incident.description = trigger.getMessage();
        incident.severity = rule.getIncidentSeverity();
        incident.fingerprint = fingerprint;
        incident.rule = rule;
        incident.sourceEvent = trigger;
        incident.service = trigger.getService();
        incident.device = trigger.getDevice();
        incident.eventCount = 1;
        incident.lastEventAt = trigger.getOccurredAt();
        return incident;
    }

    /** Declared manually by an operator. */
    public static Incident declare(String title, String description, IncidentSeverity severity,
            MonitoredService service, Device device, User createdBy) {
        Incident incident = new Incident();
        incident.title = title;
        incident.description = description;
        incident.severity = severity;
        incident.service = service;
        incident.device = device;
        incident.createdBy = createdBy;
        return incident;
    }

    public String getKey() {
        return KEY_PREFIX + getId();
    }

    public boolean isActive() {
        return status.isActive();
    }

    public boolean isAutomated() {
        return fingerprint != null;
    }

    // ---- automation ------------------------------------------------------------------------

    /** A repeat of the same problem: absorbed instead of opening a duplicate incident. */
    public void recordOccurrence(Instant occurredAt) {
        eventCount++;
        if (lastEventAt == null || occurredAt.isAfter(lastEventAt)) {
            lastEventAt = occurredAt;
        }
    }

    public void markRecovered(Instant at) {
        this.recoveredAt = at;
    }

    public void clearRecovery() {
        this.recoveredAt = null;
    }

    // ---- lifecycle -------------------------------------------------------------------------

    public void transitionTo(IncidentStatus target, Instant now, String note) {
        if (status == target) {
            throw new BusinessRuleException("STATUS_UNCHANGED", "Incident is already " + target);
        }
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Cannot change status from %s to %s".formatted(status, target));
        }
        IncidentStatus previous = status;
        status = target;
        switch (target) {
            case INVESTIGATING -> {
                if (acknowledgedAt == null) {
                    acknowledgedAt = now;
                }
            }
            case RESOLVED -> {
                resolvedAt = now;
                resolutionNote = note == null || note.isBlank() ? null : note.trim();
            }
            case OPEN -> {
                if (previous == IncidentStatus.RESOLVED) {
                    resolvedAt = null;
                    resolutionNote = null;
                    recoveredAt = null;
                }
            }
        }
    }

    public void autoResolve(Instant now, String note) {
        transitionTo(IncidentStatus.RESOLVED, now, note);
    }

    public void assignTo(User user) {
        requireActive("reassigned");
        this.assignee = user;
    }

    public void changeSeverity(IncidentSeverity newSeverity) {
        requireActive("re-prioritised");
        this.severity = newSeverity;
    }

    private void requireActive(String action) {
        if (!isActive()) {
            throw new BusinessRuleException("INCIDENT_RESOLVED",
                    "Resolved incidents cannot be " + action + ". Reopen the incident first.");
        }
    }

    // ---- seed / import support -------------------------------------------------------------

    /** Restores lifecycle timestamps for back-dated data. Not used by request handling. */
    public void restoreLifecycle(IncidentStatus status, User assignee, int eventCount, Instant lastEventAt,
            Instant acknowledgedAt, Instant recoveredAt, Instant resolvedAt, String resolutionNote) {
        this.status = status;
        this.assignee = assignee;
        this.eventCount = eventCount;
        this.lastEventAt = lastEventAt;
        this.acknowledgedAt = acknowledgedAt;
        this.recoveredAt = recoveredAt;
        this.resolvedAt = resolvedAt;
        this.resolutionNote = resolutionNote;
    }

    // ---- accessors -------------------------------------------------------------------------

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public AlertRule getRule() {
        return rule;
    }

    public Event getSourceEvent() {
        return sourceEvent;
    }

    public MonitoredService getService() {
        return service;
    }

    public Device getDevice() {
        return device;
    }

    public User getAssignee() {
        return assignee;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public int getEventCount() {
        return eventCount;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public Instant getRecoveredAt() {
        return recoveredAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Long getVersion() {
        return version;
    }
}
