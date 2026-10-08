package com.dispatch.incident;

public enum IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean isAtLeast(IncidentSeverity other) {
        return compareTo(other) >= 0;
    }
}
