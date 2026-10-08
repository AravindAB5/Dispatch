package com.dispatch.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.dispatch.event.EventSeverity;
import com.dispatch.event.SourceType;
import com.dispatch.rule.evaluation.EventFacts;
import com.dispatch.support.Fixtures;

import org.junit.jupiter.api.Test;

class IncidentFingerprintTest {

    private static EventFacts facts(SourceType type, String sourceId, Long serviceId, Long deviceId) {
        return new EventFacts("ERROR_RATE_HIGH", EventSeverity.HIGH, type, sourceId, serviceId, deviceId, Map.of());
    }

    @Test
    void identifiesProblemsByRuleAndSource() {
        var rule = Fixtures.offlineRule(1);
        assertThat(IncidentFingerprint.of(rule, facts(SourceType.DEVICE, "DEVICE-108", 3L, 8L)))
                .isEqualTo("rule:1|device:8");
        assertThat(IncidentFingerprint.of(rule, facts(SourceType.SERVICE, "content-api", 2L, null)))
                .isEqualTo("rule:1|service:2");
        assertThat(IncidentFingerprint.of(rule, facts(SourceType.EXTERNAL, " grafana ", null, null)))
                .isEqualTo("rule:1|external:GRAFANA");
    }

    @Test
    void oneExternalProducerReportingForSeveralServicesYieldsSeparateProblems() {
        var rule = Fixtures.cpuRule(2);
        String forServiceTwo = IncidentFingerprint.of(rule, facts(SourceType.EXTERNAL, "grafana", 2L, null));
        String forServiceFive = IncidentFingerprint.of(rule, facts(SourceType.EXTERNAL, "grafana", 5L, null));
        assertThat(forServiceTwo).isNotEqualTo(forServiceFive).endsWith("@service:2");
    }
}
