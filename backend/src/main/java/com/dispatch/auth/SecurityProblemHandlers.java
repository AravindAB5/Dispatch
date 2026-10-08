package com.dispatch.auth;

import java.io.IOException;

import com.dispatch.common.error.Problems;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Writes 401/403 responses produced by the security filter chain as problem details, matching the
 * format of every other API error.
 */
@Component
public class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityProblemHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String detail;
        String code;
        if (exception instanceof InvalidBearerTokenException) {
            code = "TOKEN_INVALID";
            detail = "Your session is invalid or has expired. Sign in again.";
        } else if (exception instanceof BadCredentialsException
                && request.getHeader(ApiKeyAuthenticationFilter.HEADER) != null) {
            code = "API_KEY_INVALID";
            detail = "The X-API-Key header does not match a configured ingestion key.";
        } else {
            code = "UNAUTHENTICATED";
            detail = "Authentication is required. Provide a bearer token or an ingestion API key.";
        }
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, Problems.of(HttpStatus.UNAUTHORIZED, "Unauthorized", detail, code));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        write(response, Problems.of(HttpStatus.FORBIDDEN, "Forbidden",
                "Your role does not allow this action.", "FORBIDDEN"));
    }

    private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Problems.toMap(problem));
    }
}
