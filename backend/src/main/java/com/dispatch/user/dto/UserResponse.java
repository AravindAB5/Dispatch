package com.dispatch.user.dto;

import java.time.Instant;

import com.dispatch.user.Role;
import com.dispatch.user.User;

public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        boolean active,
        long activeIncidents,
        Instant createdAt) {

    public static UserResponse from(User user, long activeIncidents) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive(),
                activeIncidents, user.getCreatedAt());
    }
}
