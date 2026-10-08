package com.dispatch.device.dto;

import java.time.Instant;

import com.dispatch.device.Device;
import com.dispatch.device.DeviceStatus;
import com.dispatch.service.dto.ServiceRef;

public record DeviceResponse(
        Long id,
        String identifier,
        String name,
        String deviceType,
        String location,
        DeviceStatus status,
        Instant lastSeenAt,
        ServiceRef service,
        long activeIncidents,
        Instant createdAt,
        Instant updatedAt) {

    public static DeviceResponse from(Device d, long activeIncidents) {
        return new DeviceResponse(d.getId(), d.getIdentifier(), d.getName(), d.getDeviceType(), d.getLocation(),
                d.getStatus(), d.getLastSeenAt(), ServiceRef.from(d.getService()), activeIncidents, d.getCreatedAt(),
                d.getUpdatedAt());
    }
}
