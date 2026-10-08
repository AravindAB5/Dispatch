package com.dispatch.support;

import java.math.BigDecimal;
import java.time.Instant;

import com.dispatch.device.Device;
import com.dispatch.event.Event;
import com.dispatch.event.EventSeverity;
import com.dispatch.event.EventTypes;
import com.dispatch.event.SourceType;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.rule.AlertRule;
import com.dispatch.rule.ComparisonOperator;
import com.dispatch.service.Environment;
import com.dispatch.service.MonitoredService;
import com.dispatch.user.Role;
import com.dispatch.user.User;

import org.springframework.test.util.ReflectionTestUtils;

/** In-memory domain objects for unit tests (no database). */
public final class Fixtures {

    public static final Instant NOW = Instant.parse("2026-10-08T09:41:00Z");

    private Fixtures() {
    }

    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static MonitoredService service(long id, String name) {
        return withId(new MonitoredService(name, null, Environment.PRODUCTION, "Edge Systems"), id);
    }

    public static Device device(long id, String identifier, MonitoredService service) {
        return withId(new Device(identifier, "Gateway node", "Gateway node", service, "Chennai DC"), id);
    }

    public static User user(long id, String name, Role role) {
        return withId(new User(name, name.toLowerCase().replace(' ', '.') + "@dispatch.test", "{noop}x", role), id);
    }

    public static Event deviceEvent(long id, String type, EventSeverity severity, Device device, String metadata) {
        return withId(new Event(type, severity, SourceType.DEVICE, device.getIdentifier(), device.getService(), device,
                type + " from " + device.getIdentifier(), metadata, NOW, NOW), id);
    }

    public static Event serviceEvent(long id, String type, EventSeverity severity, MonitoredService service) {
        return withId(new Event(type, severity, SourceType.SERVICE, service.slug(), service, null,
                type + " from " + service.getName(), "{}", NOW, NOW), id);
    }

    public static AlertRule offlineRule(long id) {
        return withId(AlertRule.onEventType("Device offline", EventTypes.DEVICE_OFFLINE, IncidentSeverity.CRITICAL)
                .recoveredBy(EventTypes.DEVICE_RECOVERED)
                .titled("{source} is offline"), id);
    }

    public static AlertRule cpuRule(long id) {
        return withId(AlertRule.onMetric("High CPU usage", EventTypes.CPU_USAGE_HIGH, "cpu", ComparisonOperator.GT,
                        new BigDecimal("90"), IncidentSeverity.HIGH)
                .recoveredBy(EventTypes.CPU_USAGE_NORMAL)
                .titled("High CPU usage on {source} ({value}%)"), id);
    }

    public static AlertRule outageRule(long id) {
        return withId(AlertRule.onEventType("Service outage", EventTypes.SERVICE_DOWN, IncidentSeverity.CRITICAL)
                .recoveredBy(EventTypes.SERVICE_RECOVERED)
                .autoResolve(false)
                .titled("{service} is down"), id);
    }
}
