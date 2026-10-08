package com.dispatch.incident;

/** How an event relates to an incident. */
public enum IncidentEventRelation {
    /** The event that opened the incident. */
    TRIGGER,
    /** A repeat of the same problem, absorbed by de-duplication. */
    OCCURRENCE,
    /** The recovery signal for the problem. */
    RECOVERY
}
