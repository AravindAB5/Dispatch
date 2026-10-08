package com.dispatch.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Machine-to-machine authentication for event producers (devices, collectors, CI hooks).
 * A request carrying a valid {@code X-API-Key} header is authenticated with the INGEST role, which is only
 * allowed to submit events. Requests without the header fall through to JWT authentication.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String INGEST_PRINCIPAL = "ingest-api-key";

    private final byte[] expectedKey;
    private final AuthenticationEntryPoint entryPoint;
    private final SecurityContextHolderStrategy contextStrategy = SecurityContextHolder.getContextHolderStrategy();

    public ApiKeyAuthenticationFilter(String expectedKey, AuthenticationEntryPoint entryPoint) {
        if (expectedKey == null || expectedKey.isBlank()) {
            throw new IllegalStateException("DISPATCH_INGEST_API_KEY must be configured");
        }
        this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null) {
            chain.doFilter(request, response);
            return;
        }
        // Constant-time comparison to avoid leaking the key through response timing.
        if (!MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedKey)) {
            contextStrategy.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException("Invalid API key"));
            return;
        }
        SecurityContext context = contextStrategy.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                INGEST_PRINCIPAL, null, List.of(new SimpleGrantedAuthority("ROLE_INGEST"))));
        contextStrategy.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            contextStrategy.clearContext();
        }
    }
}
