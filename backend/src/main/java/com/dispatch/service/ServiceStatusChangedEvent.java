package com.dispatch.service;

import java.time.Instant;

/** Published when derived service health changes; broadcast to clients as {@code service.updated}. */
public record ServiceStatusChangedEvent(
        Long id,
        String name,
        ServiceStatus status,
        ServiceStatus previousStatus,
        Instant statusChangedAt) {
}
