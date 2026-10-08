package com.dispatch.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dispatch.security")
public record SecurityProperties(Jwt jwt, String ingestApiKey) {

    public record Jwt(String secret, String issuer, Duration ttl) {
    }
}
