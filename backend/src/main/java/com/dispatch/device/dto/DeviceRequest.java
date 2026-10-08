package com.dispatch.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$",
                message = "may only contain letters, digits, '.', '_' and '-'")
        String identifier,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 64) String deviceType,
        @NotNull Long serviceId,
        @Size(max = 120) String location) {
}
