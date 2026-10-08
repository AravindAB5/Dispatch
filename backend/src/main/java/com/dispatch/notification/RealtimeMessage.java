package com.dispatch.notification;

import java.time.Instant;

/**
 * Envelope for every WebSocket message: {@code {"type": "incident.created", "data": {...}, "sentAt": "..."}}.
 */
public record RealtimeMessage(String type, Object data, Instant sentAt) {
}
