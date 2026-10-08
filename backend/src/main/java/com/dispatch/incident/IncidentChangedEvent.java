package com.dispatch.incident;

import com.dispatch.incident.dto.IncidentSummary;

/**
 * Published inside the transaction that changed an incident and broadcast to clients only after that
 * transaction commits. Carries a snapshot so no extra query is needed after commit.
 */
public record IncidentChangedEvent(ChangeType type, IncidentSummary incident) {

    public enum ChangeType {
        CREATED("incident.created"),
        UPDATED("incident.updated"),
        RESOLVED("incident.resolved");

        private final String messageType;

        ChangeType(String messageType) {
            this.messageType = messageType;
        }

        public String messageType() {
            return messageType;
        }
    }
}
