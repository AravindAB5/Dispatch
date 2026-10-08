package com.dispatch.auth;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Browser origins allowed to call the REST API and open the WebSocket. */
@ConfigurationProperties(prefix = "dispatch.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream().map(String::trim).toList();
    }
}
