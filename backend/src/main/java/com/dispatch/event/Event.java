package com.dispatch.event;

import java.time.Instant;
import java.util.Objects;

import com.dispatch.device.Device;
import com.dispatch.service.MonitoredService;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.Hibernate;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Something reported to Dispatch. Events are immutable once recorded.
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EventSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private SourceType sourceType;

    @Column(name = "source_id", nullable = false, length = 128)
    private String sourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private MonitoredService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(nullable = false, length = 1000)
    private String message;

    /** Raw JSON object. Stored as JSONB; (de)serialized by {@link EventMetadataCodec}. */
    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String metadata = "{}";

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected Event() {
    }

    public Event(String eventType, EventSeverity severity, SourceType sourceType, String sourceId,
            MonitoredService service, Device device, String message, String metadata, Instant occurredAt,
            Instant receivedAt) {
        this.eventType = eventType;
        this.severity = severity;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.service = service;
        this.device = device;
        this.message = message;
        this.metadata = metadata == null ? "{}" : metadata;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }

    @PrePersist
    void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public EventSeverity getSeverity() {
        return severity;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public MonitoredService getService() {
        return service;
    }

    public Device getDevice() {
        return device;
    }

    public String getMessage() {
        return message;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        return id != null && Objects.equals(id, ((Event) other).getId());
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
