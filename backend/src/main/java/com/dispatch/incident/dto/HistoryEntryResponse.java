package com.dispatch.incident.dto;

import java.time.Instant;

import com.dispatch.incident.HistoryAction;
import com.dispatch.incident.IncidentHistory;
import com.dispatch.user.dto.UserSummary;

/** A timeline entry. {@code actor} is null for changes made automatically by Dispatch. */
public record HistoryEntryResponse(
        Long id,
        HistoryAction action,
        String fromValue,
        String toValue,
        String message,
        UserSummary actor,
        Instant createdAt) {

    public static HistoryEntryResponse from(IncidentHistory h) {
        return new HistoryEntryResponse(h.getId(), h.getAction(), h.getFromValue(), h.getToValue(), h.getMessage(),
                UserSummary.from(h.getActor()), h.getCreatedAt());
    }
}
