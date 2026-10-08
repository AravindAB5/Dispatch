package com.dispatch.event;

import java.util.List;

import com.dispatch.device.dto.DeviceRef;
import com.dispatch.event.dto.EventResponse;
import com.dispatch.incident.dto.EventIncidentLink;
import com.dispatch.service.dto.ServiceRef;

import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    private final EventMetadataCodec metadataCodec;

    public EventMapper(EventMetadataCodec metadataCodec) {
        this.metadataCodec = metadataCodec;
    }

    public EventResponse toResponse(Event event, List<EventIncidentLink> incidents) {
        return new EventResponse(
                event.getId(),
                event.getEventType(),
                event.getSeverity(),
                event.getSourceType(),
                event.getSourceId(),
                ServiceRef.from(event.getService()),
                DeviceRef.from(event.getDevice()),
                event.getMessage(),
                metadataCodec.decode(event.getMetadata()),
                event.getOccurredAt(),
                event.getReceivedAt(),
                incidents == null ? List.of() : incidents);
    }
}
