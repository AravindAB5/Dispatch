package com.dispatch.device;

/**
 * OFFLINE is driven by connectivity events; ONLINE/DEGRADED are derived from the device's active incidents.
 */
public enum DeviceStatus {
    ONLINE,
    DEGRADED,
    OFFLINE
}
