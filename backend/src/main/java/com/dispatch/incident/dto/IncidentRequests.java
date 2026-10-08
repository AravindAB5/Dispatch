package com.dispatch.incident.dto;

import com.dispatch.incident.IncidentSeverity;
import com.dispatch.incident.IncidentStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies for the incident command endpoints. */
public final class IncidentRequests {

    private IncidentRequests() {
    }

    public record Declare(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull IncidentSeverity severity,
            Long serviceId,
            Long deviceId) {
    }

    /** {@code assigneeId: null} unassigns the incident. */
    public record Assign(Long assigneeId) {
    }

    public record ChangeStatus(
            @NotNull IncidentStatus status,
            @Size(max = 2000) String note) {
    }

    public record ChangeSeverity(@NotNull IncidentSeverity severity) {
    }

    public record AddComment(@NotBlank @Size(max = 5000) String body) {
    }
}
