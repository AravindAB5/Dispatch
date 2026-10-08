package com.dispatch.rule.evaluation;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import com.dispatch.event.Event;
import com.dispatch.event.EventSeverity;
import com.dispatch.event.SourceType;

/**
 * The attributes of an event that rules can look at. Decoupled from the JPA entity so that rule
 * evaluation is a pure function and trivially unit-testable.
 */
public record EventFacts(
        String eventType,
        EventSeverity severity,
        SourceType sourceType,
        String sourceId,
        Long serviceId,
        Long deviceId,
        Map<String, Object> metadata) {

    public EventFacts {
        metadata = metadata == null ? Map.of() : metadata;
    }

    public static EventFacts of(Event event, Map<String, Object> metadata) {
        return new EventFacts(
                event.getEventType(),
                event.getSeverity(),
                event.getSourceType(),
                event.getSourceId(),
                event.getService() == null ? null : event.getService().getId(),
                event.getDevice() == null ? null : event.getDevice().getId(),
                metadata);
    }

    /**
     * Reads a numeric metadata value. Supports dotted paths into nested objects ({@code "disk.root"})
     * and numeric strings ({@code "94.5"}).
     */
    public Optional<BigDecimal> metric(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Object current = metadata;
        for (String segment : key.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return Optional.empty();
            }
            current = map.get(segment);
        }
        return toDecimal(current);
    }

    private static Optional<BigDecimal> toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return Optional.of(decimal);
        }
        if (value instanceof Number number) {
            return Optional.of(new BigDecimal(number.toString()));
        }
        if (value instanceof String text) {
            try {
                return Optional.of(new BigDecimal(text.trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
