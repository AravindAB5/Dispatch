package com.dispatch.incident.dto;

import com.dispatch.incident.IncidentEventRelation;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.incident.IncidentStatus;

/** JPQL constructor-expression projection: which incidents an event is linked to. */
public record EventIncidentLinkRow(
        Long eventId,
        Long incidentId,
        String title,
        IncidentSeverity severity,
        IncidentStatus status,
        IncidentEventRelation relation) {

    public EventIncidentLink toLink() {
        return new EventIncidentLink(incidentId, "INC-" + incidentId, title, severity, status, relation);
    }
}
