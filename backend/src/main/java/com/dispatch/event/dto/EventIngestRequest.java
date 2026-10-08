package com.dispatch.event.dto;

import java.time.Instant;
import java.util.Map;

import com.dispatch.event.EventSeverity;
import com.dispatch.event.SourceType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Payload accepted by {@code POST /api/v1/events}.
 * <pre>
 * {
 *   "eventType": "CPU_USAGE_HIGH",
 *   "severity": "HIGH",
 *   "sourceType": "DEVICE",
 *   "sourceId": "DEVICE-042",
 *   "serviceId": 3,
 *   "message": "CPU usage reached 94%",
 *   "metadata": { "cpu": 94, "threshold": 90 },
 *   "timestamp": "2026-10-08T10:41:12Z"
 * }
 * </pre>
 */
public record EventIngestRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "must be UPPER_SNAKE_CASE, e.g. DEVICE_OFFLINE")
        String eventType,

        @NotNull
        EventSeverity severity,

        @NotNull
        SourceType sourceType,

        @NotBlank
        @Size(max = 128)
        String sourceId,

        @Positive
        Long serviceId,

        @NotBlank
        @Size(max = 1000)
        String message,

        @Size(max = 50, message = "may contain at most 50 keys")
        Map<String, Object> metadata,

        /** When the event happened at the source. Defaults to the time it was received. */
        Instant timestamp) {
}
