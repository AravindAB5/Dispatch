package com.dispatch.incident;

import java.time.Instant;
import java.util.List;

public record IncidentFilter(
        List<IncidentStatus> statuses,
        List<IncidentSeverity> severities,
        Long serviceId,
        Long deviceId,
        Long assigneeId,
        boolean unassignedOnly,
        String query,
        Instant createdFrom,
        Instant createdTo) {

    public static IncidentFilter active() {
        return new IncidentFilter(List.copyOf(IncidentStatus.ACTIVE), List.of(), null, null, null, false, null,
                null, null);
    }
}
