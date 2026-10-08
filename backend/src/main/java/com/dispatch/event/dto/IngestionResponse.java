package com.dispatch.event.dto;

import java.util.List;

import com.dispatch.incident.dto.IncidentAction;

/** The stored event plus what alert processing did with it. */
public record IngestionResponse(EventResponse event, List<IncidentAction> incidentActions) {
}
