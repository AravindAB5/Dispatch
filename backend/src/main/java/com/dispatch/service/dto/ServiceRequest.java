package com.dispatch.service.dto;

import com.dispatch.service.Environment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ServiceRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull Environment environment,
        @Size(max = 120) String ownerTeam) {
}
