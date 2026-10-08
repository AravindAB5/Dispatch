package com.dispatch.incident;

import java.time.Instant;

import com.dispatch.event.Event;

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
import jakarta.persistence.Table;

/** Link between an incident and an event that contributed to it. */
@Entity
@Table(name = "incident_events")
public class IncidentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentEventRelation relation;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    protected IncidentEvent() {
    }

    public IncidentEvent(Incident incident, Event event, IncidentEventRelation relation, Instant linkedAt) {
        this.incident = incident;
        this.event = event;
        this.relation = relation;
        this.linkedAt = linkedAt;
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public Event getEvent() {
        return event;
    }

    public IncidentEventRelation getRelation() {
        return relation;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }
}
