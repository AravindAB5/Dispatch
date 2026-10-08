package com.dispatch.device;

import java.time.Instant;

/** Published when a device's status changes; broadcast to clients as {@code device.updated}. */
public record DeviceStatusChangedEvent(
        Long id,
        String identifier,
        String name,
        Long serviceId,
        DeviceStatus status,
        DeviceStatus previousStatus,
        Instant lastSeenAt) {

    public static DeviceStatusChangedEvent of(Device device, DeviceStatus previous) {
        return new DeviceStatusChangedEvent(device.getId(), device.getIdentifier(), device.getName(),
                device.getService().getId(), device.getStatus(), previous, device.getLastSeenAt());
    }
}
