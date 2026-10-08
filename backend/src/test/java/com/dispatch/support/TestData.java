package com.dispatch.support;

import java.math.BigDecimal;

import com.dispatch.device.Device;
import com.dispatch.device.DeviceRepository;
import com.dispatch.event.EventTypes;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.rule.AlertRule;
import com.dispatch.rule.AlertRuleRepository;
import com.dispatch.rule.ComparisonOperator;
import com.dispatch.service.Environment;
import com.dispatch.service.MonitoredService;
import com.dispatch.service.MonitoredServiceRepository;
import com.dispatch.user.Role;
import com.dispatch.user.User;
import com.dispatch.user.UserRepository;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Resets the database to a small, known fixture before every integration test. */
@TestComponent
public class TestData {

    public static final String ADMIN_EMAIL = "admin@test.local";
    public static final String OPERATOR_EMAIL = "operator@test.local";
    public static final String INACTIVE_EMAIL = "former@test.local";
    public static final String PASSWORD = "correct-horse-battery";
    public static final String INGEST_KEY = "dispatch-dev-ingest-key";

    private static String passwordHash;

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final MonitoredServiceRepository services;
    private final DeviceRepository devices;
    private final AlertRuleRepository rules;
    private final PasswordEncoder passwordEncoder;

    public User admin;
    public User operator;
    public User inactive;
    public MonitoredService gateway;
    public MonitoredService notifications;
    public Device device108;
    public Device device042;
    public AlertRule offlineRule;
    public AlertRule cpuRule;
    public AlertRule outageRule;

    public TestData(JdbcTemplate jdbc, UserRepository users, MonitoredServiceRepository services,
            DeviceRepository devices, AlertRuleRepository rules, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.users = users;
        this.services = services;
        this.devices = devices;
        this.rules = rules;
        this.passwordEncoder = passwordEncoder;
    }

    public void reset() {
        jdbc.execute("""
                TRUNCATE TABLE incident_history, incident_comments, incident_events, incidents, events,
                               alert_rules, devices, services, users RESTART IDENTITY CASCADE
                """);
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        admin = users.save(new User("Test Admin", ADMIN_EMAIL, passwordHash, Role.ADMIN));
        operator = users.save(new User("Test Operator", OPERATOR_EMAIL, passwordHash, Role.OPERATOR));
        User former = new User("Former Operator", INACTIVE_EMAIL, passwordHash, Role.OPERATOR);
        former.setActive(false);
        inactive = users.save(former);

        gateway = services.save(new MonitoredService("Device Gateway", null, Environment.PRODUCTION, "Edge"));
        notifications = services.save(new MonitoredService("Notification Service", null, Environment.PRODUCTION,
                "Engagement"));
        device108 = devices.save(new Device("DEVICE-108", "Gateway node 03", "Gateway node", gateway, "Chennai"));
        device042 = devices.save(new Device("DEVICE-042", "Gateway node 01", "Gateway node", gateway, "Mumbai"));

        offlineRule = rules.save(AlertRule.onEventType("Device offline", EventTypes.DEVICE_OFFLINE,
                        IncidentSeverity.CRITICAL)
                .recoveredBy(EventTypes.DEVICE_RECOVERED)
                .titled("{source} is offline"));
        cpuRule = rules.save(AlertRule.onMetric("High CPU usage", EventTypes.CPU_USAGE_HIGH, "cpu",
                        ComparisonOperator.GT, new BigDecimal("90"), IncidentSeverity.HIGH)
                .recoveredBy(EventTypes.CPU_USAGE_NORMAL)
                .titled("High CPU usage on {source}"));
        outageRule = rules.save(AlertRule.onEventType("Service outage", EventTypes.SERVICE_DOWN,
                        IncidentSeverity.CRITICAL)
                .recoveredBy(EventTypes.SERVICE_RECOVERED)
                .autoResolve(false)
                .titled("{service} is down"));
    }
}
