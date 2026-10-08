package com.dispatch.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.dispatch.device.Device;
import com.dispatch.event.Event;
import com.dispatch.event.EventSeverity;
import com.dispatch.event.EventTypes;
import com.dispatch.rule.AlertRule;
import com.dispatch.service.MonitoredService;
import com.dispatch.rule.evaluation.EventFacts;
import com.dispatch.support.Fixtures;

import org.junit.jupiter.api.Test;

class IncidentTitlesTest {

    private final MonitoredService gateway = Fixtures.service(3, "Device Gateway");
    private final Device device = Fixtures.device(8, "DEVICE-108", gateway);

    private String render(AlertRule rule, Event event, Map<String, Object> metadata) {
        return IncidentTitles.render(rule, event, EventFacts.of(event, metadata));
    }

    @Test
    void substitutesSourceDeviceAndServicePlaceholders() {
        Event offline = Fixtures.deviceEvent(1, EventTypes.DEVICE_OFFLINE, EventSeverity.CRITICAL, device, "{}");
        assertThat(render(Fixtures.offlineRule(1), offline, Map.of())).isEqualTo("DEVICE-108 is offline");

        AlertRule rule = Fixtures.offlineRule(1).titled("{device} in {service} ({eventType})");
        assertThat(render(rule, offline, Map.of()))
                .isEqualTo("DEVICE-108 in Device Gateway (DEVICE_OFFLINE)");
    }

    @Test
    void serviceLevelEventsUseTheServiceNameAsSource() {
        Event down = Fixtures.serviceEvent(2, EventTypes.SERVICE_DOWN, EventSeverity.CRITICAL, gateway);
        AlertRule rule = Fixtures.outageRule(5).titled("{source} is down");
        assertThat(render(rule, down, Map.of())).isEqualTo("Device Gateway is down");
    }

    @Test
    void formatsMetricValuesWithoutTrailingZeros() {
        Event cpu = Fixtures.deviceEvent(3, EventTypes.CPU_USAGE_HIGH, EventSeverity.HIGH, device, "{}");
        assertThat(render(Fixtures.cpuRule(2), cpu, Map.of("cpu", 94.50)))
                .isEqualTo("High CPU usage on DEVICE-108 (94.5%)");
    }

    @Test
    void fallsBackToTheDefaultTemplateAndKeepsUnknownPlaceholders() {
        Event offline = Fixtures.deviceEvent(1, EventTypes.DEVICE_OFFLINE, EventSeverity.CRITICAL, device, "{}");
        AlertRule noTemplate = AlertRule.onEventType("Device offline", EventTypes.DEVICE_OFFLINE,
                IncidentSeverity.CRITICAL);
        assertThat(render(noTemplate, offline, Map.of())).isEqualTo("Device offline: DEVICE-108");
        assertThat(render(noTemplate.titled("{nope} on {source}"), offline, Map.of()))
                .isEqualTo("{nope} on DEVICE-108");
    }

    @Test
    void truncatesVeryLongTitles() {
        Event offline = Fixtures.deviceEvent(1, EventTypes.DEVICE_OFFLINE, EventSeverity.CRITICAL, device, "{}");
        AlertRule rule = Fixtures.offlineRule(1).titled("x".repeat(250));
        assertThat(render(rule, offline, Map.of())).hasSize(200).endsWith("…");
    }
}
