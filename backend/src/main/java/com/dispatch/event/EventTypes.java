package com.dispatch.event;

/**
 * Well-known event types. Event types are open-ended strings (any UPPER_SNAKE_CASE value is accepted);
 * only the connectivity types below carry built-in meaning, everything else is interpreted by alert rules.
 */
public final class EventTypes {

    public static final String DEVICE_HEARTBEAT = "DEVICE_HEARTBEAT";
    public static final String DEVICE_OFFLINE = "DEVICE_OFFLINE";
    public static final String DEVICE_RECOVERED = "DEVICE_RECOVERED";

    public static final String CPU_USAGE_HIGH = "CPU_USAGE_HIGH";
    public static final String CPU_USAGE_NORMAL = "CPU_USAGE_NORMAL";
    public static final String MEMORY_USAGE_HIGH = "MEMORY_USAGE_HIGH";
    public static final String MEMORY_USAGE_NORMAL = "MEMORY_USAGE_NORMAL";
    public static final String DISK_USAGE_HIGH = "DISK_USAGE_HIGH";
    public static final String DISK_USAGE_NORMAL = "DISK_USAGE_NORMAL";

    public static final String ERROR_RATE_HIGH = "ERROR_RATE_HIGH";
    public static final String ERROR_RATE_NORMAL = "ERROR_RATE_NORMAL";
    public static final String LATENCY_HIGH = "LATENCY_HIGH";
    public static final String LATENCY_NORMAL = "LATENCY_NORMAL";
    public static final String SERVICE_DEGRADED = "SERVICE_DEGRADED";
    public static final String SERVICE_DOWN = "SERVICE_DOWN";
    public static final String SERVICE_RECOVERED = "SERVICE_RECOVERED";

    public static final String DEPLOYMENT_COMPLETED = "DEPLOYMENT_COMPLETED";
    public static final String CONFIG_RELOADED = "CONFIG_RELOADED";
    public static final String CACHE_PURGED = "CACHE_PURGED";
    public static final String CERTIFICATE_RENEWED = "CERTIFICATE_RENEWED";

    private EventTypes() {
    }
}
