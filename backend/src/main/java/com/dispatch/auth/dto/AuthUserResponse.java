package com.dispatch.auth.dto;

import com.dispatch.user.Role;
import com.dispatch.user.User;

public record AuthUserResponse(Long id, String name, String email, Role role) {

    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
