package com.dispatch.event;

import com.dispatch.event.dto.EventResponse;

/** Published after an event has been stored and processed; broadcast to clients as {@code event.created}. */
public record EventRecordedEvent(EventResponse event) {
}
