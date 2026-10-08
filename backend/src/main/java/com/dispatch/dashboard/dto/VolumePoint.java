package com.dispatch.dashboard.dto;

import java.time.Instant;

public record VolumePoint(Instant bucketStart, long total, long problems) {
}
