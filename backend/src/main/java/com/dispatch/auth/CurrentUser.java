package com.dispatch.auth;

import com.dispatch.common.error.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/** Resolves the acting user's id from the validated JWT ({@code sub} claim). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AuthException(HttpStatus.FORBIDDEN, "USER_REQUIRED", "This action requires a signed-in user");
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "The access token subject is invalid");
        }
    }

    public static class AuthException extends ApiException {
        public AuthException(HttpStatus status, String code, String message) {
            super(status, code, message);
        }
    }
}
