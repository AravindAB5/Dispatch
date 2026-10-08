package com.dispatch.incident.dto;

import java.time.Instant;

import com.dispatch.incident.IncidentComment;
import com.dispatch.user.dto.UserSummary;

public record CommentResponse(Long id, Long incidentId, String body, UserSummary author, Instant createdAt) {

    public static CommentResponse from(IncidentComment c) {
        return new CommentResponse(c.getId(), c.getIncident().getId(), c.getBody(), UserSummary.from(c.getAuthor()),
                c.getCreatedAt());
    }
}
