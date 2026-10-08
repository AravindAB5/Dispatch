package com.dispatch.dashboard.dto;

import java.time.Instant;

public record DashboardSummary(
        ActiveIncidents activeIncidents,
        Throughput last24h,
        ResponseTimes responseTimes,
        Volume events,
        HealthCounts services,
        HealthCounts devices,
        Instant generatedAt) {

    public record ActiveIncidents(long total, long critical, long high, long medium, long low, long open,
            long investigating, long unassigned) {
    }

    public record Throughput(long created, long resolved) {
    }

    /** Mean time to acknowledge / resolve over the trailing window, in seconds (null when no data). */
    public record ResponseTimes(Long mttaSeconds, Long mttrSeconds, int windowDays) {
    }

    public record Volume(long lastHour, long last24h) {
    }

    /** For services: healthy/degraded/down. For devices: online/degraded/offline (reported as healthy/degraded/down). */
    public record HealthCounts(long total, long healthy, long degraded, long down) {
    }
}
