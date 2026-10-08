package com.dispatch.incident;

import com.dispatch.event.SourceType;
import com.dispatch.rule.AlertRule;
import com.dispatch.rule.evaluation.EventFacts;

/**
 * Identifies "the same ongoing problem": one alert rule firing for one source.
 * Repeated DEVICE_OFFLINE events from DEVICE-108 share a fingerprint and therefore one incident,
 * while DEVICE-108 and DEVICE-042 going offline are two separate problems.
 */
public final class IncidentFingerprint {

    private IncidentFingerprint() {
    }

    public static String of(AlertRule rule, EventFacts facts) {
        return "rule:" + rule.getId() + "|" + sourceKey(facts);
    }

    static String sourceKey(EventFacts facts) {
        if (facts.deviceId() != null) {
            return "device:" + facts.deviceId();
        }
        if (facts.sourceType() == SourceType.SERVICE && facts.serviceId() != null) {
            return "service:" + facts.serviceId();
        }
        // External producers (e.g. one monitoring tool) may report for several services: keep them apart.
        String key = facts.sourceType().name().toLowerCase() + ":" + facts.sourceId().trim().toUpperCase();
        return facts.serviceId() == null ? key : key + "@service:" + facts.serviceId();
    }
}
