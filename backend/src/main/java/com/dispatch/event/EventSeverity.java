package com.dispatch.event;

/** Severity reported by the event source. INFO events are informational and rarely become incidents. */
public enum EventSeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean isAtLeast(EventSeverity other) {
        return compareTo(other) >= 0;
    }
}
