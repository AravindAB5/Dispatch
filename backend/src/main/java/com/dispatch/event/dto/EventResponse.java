package com.dispatch.event.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.dispatch.device.dto.DeviceRef;
import com.dispatch.event.EventSeverity;
import com.dispatch.event.SourceType;
import com.dispatch.incident.dto.EventIncidentLink;
import com.dispatch.service.dto.ServiceRef;

public record EventResponse(
        Long id,
        String eventType,
        EventSeverity severity,
        SourceType sourceType,
        String sourceId,
        ServiceRef service,
        DeviceRef device,
        String message,
        Map<String, Object> metadata,
        Instant occurredAt,
        Instant receivedAt,
        List<EventIncidentLink> incidents) {
}
