package com.dispatch.user.dto;

import com.dispatch.user.Role;

import jakarta.validation.constraints.Size;

/** Partial update: null fields are left unchanged. */
public record UpdateUserRequest(
        @Size(min = 1, max = 120) String name,
        Role role,
        Boolean active) {
}
