package com.dispatch.user.dto;

import com.dispatch.user.User;

/** Compact user reference embedded in other resources (assignee, comment author, actor). */
public record UserSummary(Long id, String name, String email) {

    public static UserSummary from(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getName(), user.getEmail());
    }
}
