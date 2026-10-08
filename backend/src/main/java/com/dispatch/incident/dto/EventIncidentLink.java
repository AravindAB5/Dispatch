package com.dispatch.incident.dto;

import com.dispatch.incident.IncidentEventRelation;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.incident.IncidentStatus;

/** An incident an event contributed to, shown next to the event in the stream. */
public record EventIncidentLink(
        Long id,
        String key,
        String title,
        IncidentSeverity severity,
        IncidentStatus status,
        IncidentEventRelation relation) {
}
