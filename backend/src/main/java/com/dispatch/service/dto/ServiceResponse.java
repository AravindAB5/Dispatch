package com.dispatch.service.dto;

import java.time.Instant;

import com.dispatch.service.Environment;
import com.dispatch.service.MonitoredService;
import com.dispatch.service.ServiceStatus;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        Environment environment,
        String ownerTeam,
        ServiceStatus status,
        Instant statusChangedAt,
        DeviceCounts devices,
        IncidentCounts activeIncidents,
        Instant createdAt,
        Instant updatedAt) {

    public record DeviceCounts(long total, long online, long degraded, long offline) {
        public static final DeviceCounts EMPTY = new DeviceCounts(0, 0, 0, 0);
    }

    public record IncidentCounts(long total, long critical, long high, long medium, long low) {
        public static final IncidentCounts EMPTY = new IncidentCounts(0, 0, 0, 0, 0);
    }

    public static ServiceResponse from(MonitoredService s, DeviceCounts devices, IncidentCounts incidents) {
        return new ServiceResponse(s.getId(), s.getName(), s.getDescription(), s.getEnvironment(), s.getOwnerTeam(),
                s.getStatus(), s.getStatusChangedAt(), devices, incidents, s.getCreatedAt(), s.getUpdatedAt());
    }
}
