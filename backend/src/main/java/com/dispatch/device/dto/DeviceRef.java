package com.dispatch.device.dto;

import com.dispatch.device.Device;
import com.dispatch.device.DeviceStatus;

/** Compact device reference embedded in other resources. */
public record DeviceRef(Long id, String identifier, String name, DeviceStatus status) {

    public static DeviceRef from(Device device) {
        return device == null ? null
                : new DeviceRef(device.getId(), device.getIdentifier(), device.getName(), device.getStatus());
    }
}
