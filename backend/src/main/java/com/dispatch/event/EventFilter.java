package com.dispatch.event;

import java.time.Instant;
import java.util.List;

public record EventFilter(
        List<String> eventTypes,
        List<EventSeverity> severities,
        Long serviceId,
        Long deviceId,
        SourceType sourceType,
        String query,
        Instant from,
        Instant to) {
}
