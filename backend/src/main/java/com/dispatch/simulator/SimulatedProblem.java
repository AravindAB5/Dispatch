package com.dispatch.simulator;

import java.time.Instant;
import java.util.Map;
import java.util.random.RandomGenerator;

import com.dispatch.event.EventSeverity;
import com.dispatch.event.EventTypes;
import com.dispatch.event.SourceType;
import com.dispatch.event.dto.EventIngestRequest;

/**
 * An ongoing simulated problem on one device or service. It emits a trigger event when it starts,
 * occasional repeats while it lasts (exercising de-duplication) and a recovery event when it clears.
 * Problems without a recovery time model outages that need a human to fix.
 */
final class SimulatedProblem {

    enum Kind {
        CPU(true), MEMORY(true), DISK(true), OFFLINE(true),
        ERROR_RATE(false), LATENCY(false), SERVICE_DEGRADED(false), SERVICE_DOWN(false);

        final boolean deviceLevel;

        Kind(boolean deviceLevel) {
            this.deviceLevel = deviceLevel;
        }
    }

    final Kind kind;
    final String sourceId;
    final Long serviceId;
    final String label;
    final Instant startedAt;
    /** When the recovery event is sent; null means the problem persists until forgotten. */
    final Instant recoverAt;
    /** Unrecovered problems stop occupying a slot after this time (the incident stays open). */
    final Instant forgetAt;
    private int repeats;

    SimulatedProblem(Kind kind, String sourceId, Long serviceId, String label, Instant startedAt, Instant recoverAt,
            Instant forgetAt) {
        this.kind = kind;
        this.sourceId = sourceId;
        this.serviceId = serviceId;
        this.label = label;
        this.startedAt = startedAt;
        this.recoverAt = recoverAt;
        this.forgetAt = forgetAt;
    }

    String describe() {
        return kind.name().toLowerCase().replace('_', ' ') + " on " + label
                + (recoverAt == null ? " (needs operator)" : "");
    }

    EventIngestRequest trigger(RandomGenerator random) {
        return switch (kind) {
            case CPU -> {
                int cpu = random.nextInt(91, 100);
                yield request(EventTypes.CPU_USAGE_HIGH, EventSeverity.HIGH,
                        "CPU usage reached " + cpu + "%", Map.of("cpu", cpu, "threshold", 90, "cores", 16));
            }
            case MEMORY -> {
                int memory = random.nextInt(90, 98);
                yield request(EventTypes.MEMORY_USAGE_HIGH, EventSeverity.MEDIUM,
                        "Memory usage at " + memory + "% of 32 GiB",
                        Map.of("memory", memory, "usedGiB", Math.round(memory * 0.32 * 10) / 10.0));
            }
            case DISK -> {
                int disk = random.nextInt(86, 96);
                yield request(EventTypes.DISK_USAGE_HIGH, EventSeverity.MEDIUM,
                        "Disk usage on /var/lib at " + disk + "%", Map.of("disk", disk, "mount", "/var/lib"));
            }
            case OFFLINE -> request(EventTypes.DEVICE_OFFLINE, EventSeverity.CRITICAL,
                    "Device stopped responding", Map.of("missedHeartbeats", 3, "lastSeenSecondsAgo", 45));
            case ERROR_RATE -> {
                double rate = Math.round(random.nextDouble(5.5, 18.0) * 10) / 10.0;
                yield request(EventTypes.ERROR_RATE_HIGH, EventSeverity.HIGH,
                        "5xx error rate at " + rate + "% over the last 5 minutes",
                        Map.of("errorRate", rate, "window", "5m", "threshold", 5));
            }
            case LATENCY -> {
                int p95 = random.nextInt(1600, 4200);
                yield request(EventTypes.LATENCY_HIGH, EventSeverity.MEDIUM,
                        "p95 latency " + p95 + " ms (threshold 1500 ms)",
                        Map.of("p95LatencyMs", p95, "p50LatencyMs", p95 / 3));
            }
            case SERVICE_DEGRADED -> request(EventTypes.SERVICE_DEGRADED, EventSeverity.HIGH,
                    "Health checks failing in 1 of 3 regions", Map.of("failingRegions", "eu-west-1", "checks", 12));
            case SERVICE_DOWN -> request(EventTypes.SERVICE_DOWN, EventSeverity.CRITICAL,
                    "Health checks failing in all regions", Map.of("failingRegions", "all", "checks", 12));
        };
    }

    EventIngestRequest repeat(RandomGenerator random) {
        repeats++;
        return switch (kind) {
            case OFFLINE -> request(EventTypes.DEVICE_OFFLINE, EventSeverity.CRITICAL,
                    "Device still unreachable (" + (3 + repeats * 3) + " missed heartbeats)",
                    Map.of("missedHeartbeats", 3 + repeats * 3));
            case CPU -> {
                int cpu = random.nextInt(91, 100);
                yield request(EventTypes.CPU_USAGE_HIGH, EventSeverity.HIGH, "CPU usage still elevated at " + cpu + "%",
                        Map.of("cpu", cpu, "threshold", 90));
            }
            default -> trigger(random);
        };
    }

    EventIngestRequest recovery(RandomGenerator random, Instant now) {
        long downtime = Math.max(1, now.getEpochSecond() - startedAt.getEpochSecond());
        return switch (kind) {
            case CPU -> {
                int cpu = random.nextInt(32, 62);
                yield request(EventTypes.CPU_USAGE_NORMAL, EventSeverity.INFO,
                        "CPU usage returned to normal (" + cpu + "%)", Map.of("cpu", cpu));
            }
            case MEMORY -> {
                int memory = random.nextInt(48, 70);
                yield request(EventTypes.MEMORY_USAGE_NORMAL, EventSeverity.INFO,
                        "Memory usage back to " + memory + "%", Map.of("memory", memory));
            }
            case DISK -> {
                int disk = random.nextInt(55, 72);
                yield request(EventTypes.DISK_USAGE_NORMAL, EventSeverity.INFO,
                        "Disk usage reduced to " + disk + "% after log rotation", Map.of("disk", disk));
            }
            case OFFLINE -> request(EventTypes.DEVICE_RECOVERED, EventSeverity.INFO,
                    "Device reconnected after " + downtime + "s", Map.of("downtimeSeconds", downtime));
            case ERROR_RATE -> request(EventTypes.ERROR_RATE_NORMAL, EventSeverity.INFO,
                    "Error rate back to 0." + random.nextInt(1, 9) + "%", Map.of("errorRate", 0.4));
            case LATENCY -> {
                int p95 = random.nextInt(180, 600);
                yield request(EventTypes.LATENCY_NORMAL, EventSeverity.INFO, "p95 latency recovered to " + p95 + " ms",
                        Map.of("p95LatencyMs", p95));
            }
            case SERVICE_DEGRADED, SERVICE_DOWN -> request(EventTypes.SERVICE_RECOVERED, EventSeverity.INFO,
                    "All health checks passing", Map.of("outageSeconds", downtime));
        };
    }

    private EventIngestRequest request(String type, EventSeverity severity, String message, Map<String, Object> metadata) {
        SourceType sourceType = kind.deviceLevel ? SourceType.DEVICE : SourceType.SERVICE;
        return new EventIngestRequest(type, severity, sourceType, sourceId, kind.deviceLevel ? null : serviceId,
                message, metadata, null);
    }
}
