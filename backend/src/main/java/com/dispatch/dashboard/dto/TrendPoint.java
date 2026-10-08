package com.dispatch.dashboard.dto;

import java.time.Instant;

/** Incidents opened per severity, and incidents resolved, within one time bucket. */
public record TrendPoint(Instant bucketStart, long critical, long high, long medium, long low, long resolved) {

    public long opened() {
        return critical + high + medium + low;
    }
}
