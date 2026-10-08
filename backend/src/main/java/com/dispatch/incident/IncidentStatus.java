package com.dispatch.incident;

import java.util.EnumSet;
import java.util.Set;

/**
 * Incident lifecycle:
 * <pre>
 *   OPEN ──acknowledge──▶ INVESTIGATING ──resolve──▶ RESOLVED
 *     │                       │                         │
 *     └────────resolve────────┴──────────◀──reopen──────┘ (to OPEN)
 * </pre>
 * INVESTIGATING may also be moved back to OPEN (handing the incident back to the queue).
 */
public enum IncidentStatus {
    OPEN,
    INVESTIGATING,
    RESOLVED;

    public static final Set<IncidentStatus> ACTIVE = EnumSet.of(OPEN, INVESTIGATING);

    public boolean isActive() {
        return this != RESOLVED;
    }

    public boolean canTransitionTo(IncidentStatus target) {
        return switch (this) {
            case OPEN -> target == INVESTIGATING || target == RESOLVED;
            case INVESTIGATING -> target == OPEN || target == RESOLVED;
            case RESOLVED -> target == OPEN;
        };
    }
}
