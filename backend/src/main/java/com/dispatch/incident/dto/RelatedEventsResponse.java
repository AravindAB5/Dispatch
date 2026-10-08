package com.dispatch.incident.dto;

import java.time.Instant;
import java.util.List;

import com.dispatch.event.dto.EventResponse;
import com.dispatch.incident.IncidentEventRelation;

/** The most recent events that contributed to an incident, plus totals per relation. */
public record RelatedEventsResponse(
        long total,
        long triggers,
        long occurrences,
        long recoveries,
        List<RelatedEvent> items) {

    public record RelatedEvent(IncidentEventRelation relation, Instant linkedAt, EventResponse event) {
    }
}
