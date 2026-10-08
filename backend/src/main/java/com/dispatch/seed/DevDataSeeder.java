package com.dispatch.seed;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.dispatch.device.Device;
import com.dispatch.device.DeviceRepository;
import com.dispatch.device.DeviceStatus;
import com.dispatch.event.Event;
import com.dispatch.event.EventMetadataCodec;
import com.dispatch.event.EventRepository;
import com.dispatch.event.EventSeverity;
import com.dispatch.event.EventTypes;
import com.dispatch.event.SourceType;
import com.dispatch.health.HealthService;
import com.dispatch.incident.HistoryAction;
import com.dispatch.incident.Incident;
import com.dispatch.incident.IncidentComment;
import com.dispatch.incident.IncidentCommentRepository;
import com.dispatch.incident.IncidentEvent;
import com.dispatch.incident.IncidentEventRelation;
import com.dispatch.incident.IncidentEventRepository;
import com.dispatch.incident.IncidentFingerprint;
import com.dispatch.incident.IncidentHistory;
import com.dispatch.incident.IncidentHistoryRepository;
import com.dispatch.incident.IncidentRepository;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.incident.IncidentStatus;
import com.dispatch.incident.IncidentTitles;
import com.dispatch.rule.AlertRule;
import com.dispatch.rule.AlertRuleRepository;
import com.dispatch.rule.ComparisonOperator;
import com.dispatch.rule.evaluation.EventFacts;
import com.dispatch.seed.SeedCatalog.SeedService;
import com.dispatch.seed.SeedCatalog.SeedUser;
import com.dispatch.service.MonitoredService;
import com.dispatch.service.MonitoredServiceRepository;
import com.dispatch.service.ServiceStatus;
import com.dispatch.user.Role;
import com.dispatch.user.User;
import com.dispatch.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Populates an empty database with a realistic, internally consistent dataset so the application looks
 * alive on first start: users, services, devices, rules, ~1,000 events over the last 48 hours, resolved
 * incident history with timelines and comments, and a dozen active incidents. Timestamps are relative to
 * the moment of seeding. Enabled with {@code DISPATCH_SEED_ENABLED=true}; never runs on a non-empty database.
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "dispatch.seed", name = "enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final Duration HISTORY = Duration.ofHours(48);

    private final UserRepository userRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final DeviceRepository deviceRepository;
    private final AlertRuleRepository ruleRepository;
    private final EventRepository eventRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository linkRepository;
    private final IncidentCommentRepository commentRepository;
    private final IncidentHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventMetadataCodec codec;
    private final HealthService healthService;
    private final Clock clock;

    private final Random random = new Random(20261008L);
    private Instant now;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, MonitoredService> services = new LinkedHashMap<>();
    private final Map<String, Device> devices = new LinkedHashMap<>();
    private final Map<String, AlertRule> rulesByEventType = new HashMap<>();
    /** Periods during which a device was offline, so no heartbeats are generated for it then. */
    private final Map<Long, List<Instant[]>> offlineWindows = new HashMap<>();
    private final Map<Long, Instant> impactedSince = new HashMap<>();
    private int events;
    private int incidents;

    private record Target(Device device, MonitoredService service) {
        static Target of(Device device) {
            return new Target(device, device.getService());
        }

        static Target of(MonitoredService service) {
            return new Target(null, service);
        }
    }

    private record Spec(EventSeverity severity, String message, Map<String, Object> metadata) {
    }

    public DevDataSeeder(UserRepository userRepository, MonitoredServiceRepository serviceRepository,
            DeviceRepository deviceRepository, AlertRuleRepository ruleRepository, EventRepository eventRepository,
            IncidentRepository incidentRepository, IncidentEventRepository linkRepository,
            IncidentCommentRepository commentRepository, IncidentHistoryRepository historyRepository,
            PasswordEncoder passwordEncoder, EventMetadataCodec codec, HealthService healthService, Clock clock) {
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.deviceRepository = deviceRepository;
        this.ruleRepository = ruleRepository;
        this.eventRepository = eventRepository;
        this.incidentRepository = incidentRepository;
        this.linkRepository = linkRepository;
        this.commentRepository = commentRepository;
        this.historyRepository = historyRepository;
        this.passwordEncoder = passwordEncoder;
        this.codec = codec;
        this.healthService = healthService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("Seed data skipped: the database already contains data");
            return;
        }
        now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        seedUsers();
        seedServicesAndDevices();
        seedRules();
        seedHistoricalIncidents();
        seedActiveIncidents();
        seedBackgroundEvents();
        finalizeHealth();
        log.info("Seeded development data: {} users, {} services, {} devices, {} rules, {} events, {} incidents",
                users.size(), services.size(), devices.size(), rulesByEventType.size() + 1, events, incidents);
    }

    // ---- reference data --------------------------------------------------------------------

    private void seedUsers() {
        Instant created = now.minus(Duration.ofDays(90));
        for (SeedUser seed : SeedCatalog.USERS) {
            User user = new User(seed.name(), seed.email(), passwordEncoder.encode(seed.password()), seed.role());
            user.setActive(seed.active());
            user.setTimestamps(created, created);
            users.put(seed.email(), userRepository.save(user));
        }
    }

    private void seedServicesAndDevices() {
        for (SeedService seed : SeedCatalog.SERVICES) {
            MonitoredService service = new MonitoredService(seed.name(), seed.description(), seed.environment(),
                    seed.team());
            Instant created = now.minus(Duration.ofDays(120));
            service.setTimestamps(created, created);
            service.setStatusChangedAt(now.minus(Duration.ofDays(3)));
            services.put(seed.name(), serviceRepository.save(service));

            int index = 1;
            for (int number : seed.deviceNumbers()) {
                String identifier = "DEVICE-%03d".formatted(number);
                String location = seed.locations().get((index - 1) % seed.locations().size());
                Device device = new Device(identifier, "%s %02d".formatted(seed.deviceType(), index),
                        seed.deviceType(), service, location);
                Instant deviceCreated = now.minus(Duration.ofDays(60 + random.nextInt(50)));
                device.setTimestamps(deviceCreated, deviceCreated);
                device.setLastSeenAt(now.minusSeconds(5 + random.nextInt(170)));
                devices.put(identifier, deviceRepository.save(device));
                index++;
            }
        }
    }

    private void seedRules() {
        Instant created = now.minus(Duration.ofDays(60));
        List<AlertRule> rules = List.of(
                AlertRule.onEventType("Device offline", EventTypes.DEVICE_OFFLINE, IncidentSeverity.CRITICAL)
                        .recoveredBy(EventTypes.DEVICE_RECOVERED)
                        .titled("{source} is offline")
                        .describedAs("A device stopped responding. Resolves automatically when it reconnects, "
                                + "unless someone is already investigating."),
                AlertRule.onMetric("High CPU usage", EventTypes.CPU_USAGE_HIGH, "cpu", ComparisonOperator.GT,
                                new BigDecimal("90"), IncidentSeverity.HIGH)
                        .recoveredBy(EventTypes.CPU_USAGE_NORMAL)
                        .titled("High CPU usage on {source} ({value}%)")
                        .describedAs("Devices report CPU above 80%; Dispatch only pages above 90%."),
                AlertRule.onMetric("Memory pressure", EventTypes.MEMORY_USAGE_HIGH, "memory", ComparisonOperator.GTE,
                                new BigDecimal("90"), IncidentSeverity.MEDIUM)
                        .recoveredBy(EventTypes.MEMORY_USAGE_NORMAL)
                        .titled("Memory pressure on {source}")
                        .describedAs("Sustained memory usage at or above 90%."),
                AlertRule.onMetric("Disk almost full", EventTypes.DISK_USAGE_HIGH, "disk", ComparisonOperator.GT,
                                new BigDecimal("85"), IncidentSeverity.LOW)
                        .recoveredBy(EventTypes.DISK_USAGE_NORMAL)
                        .titled("Disk usage at {value}% on {source}")
                        .describedAs("Gives the team time to clean up before writes start failing."),
                AlertRule.onMetric("Elevated error rate", EventTypes.ERROR_RATE_HIGH, "errorRate",
                                ComparisonOperator.GT, new BigDecimal("5"), IncidentSeverity.HIGH)
                        .recoveredBy(EventTypes.ERROR_RATE_NORMAL)
                        .titled("Elevated error rate on {service}")
                        .describedAs("More than 5% of requests failing over a 5 minute window."),
                AlertRule.onMetric("Slow responses", EventTypes.LATENCY_HIGH, "p95LatencyMs", ComparisonOperator.GT,
                                new BigDecimal("1500"), IncidentSeverity.MEDIUM)
                        .recoveredBy(EventTypes.LATENCY_NORMAL)
                        .titled("Slow responses from {service}")
                        .describedAs("p95 latency above 1.5 seconds."),
                AlertRule.onEventType("Service degraded", EventTypes.SERVICE_DEGRADED, IncidentSeverity.HIGH)
                        .recoveredBy(EventTypes.SERVICE_RECOVERED)
                        .titled("{service} is degraded")
                        .describedAs("Health checks failing in part of the fleet."),
                AlertRule.onEventType("Service outage", EventTypes.SERVICE_DOWN, IncidentSeverity.CRITICAL)
                        .recoveredBy(EventTypes.SERVICE_RECOVERED)
                        .autoResolve(false)
                        .titled("{service} is down")
                        .describedAs("Full outage. Never auto-resolves: an operator must confirm the recovery."));
        for (AlertRule rule : rules) {
            rule.setTimestamps(created, created);
            rulesByEventType.put(rule.getEventType(), ruleRepository.save(rule));
        }
        AlertRule catchAll = AlertRule.onSeverity("Critical event catch-all", EventSeverity.CRITICAL,
                        IncidentSeverity.HIGH)
                .titled("Critical event from {source}")
                .describedAs("Opens an incident for any CRITICAL event. Disabled because it overlaps with the "
                        + "specific rules above.")
                .enabled(false);
        catchAll.setTimestamps(created, created);
        ruleRepository.save(catchAll);
    }

    // ---- resolved incident history ---------------------------------------------------------

    private void seedHistoricalIncidents() {
        String[] kinds = {
                EventTypes.CPU_USAGE_HIGH, EventTypes.CPU_USAGE_HIGH, EventTypes.CPU_USAGE_HIGH,
                EventTypes.CPU_USAGE_HIGH, EventTypes.DEVICE_OFFLINE, EventTypes.DEVICE_OFFLINE,
                EventTypes.DEVICE_OFFLINE, EventTypes.MEMORY_USAGE_HIGH, EventTypes.MEMORY_USAGE_HIGH,
                EventTypes.ERROR_RATE_HIGH, EventTypes.ERROR_RATE_HIGH, EventTypes.LATENCY_HIGH,
                EventTypes.LATENCY_HIGH, EventTypes.SERVICE_DEGRADED, EventTypes.DISK_USAGE_HIGH,
                EventTypes.SERVICE_DOWN};
        List<Device> deviceList = new ArrayList<>(devices.values());
        List<MonitoredService> serviceList = new ArrayList<>(services.values());
        for (int i = 0; i < 40; i++) {
            String type = kinds[random.nextInt(kinds.length)];
            if (EventTypes.SERVICE_DOWN.equals(type) && random.nextBoolean()) {
                type = EventTypes.SERVICE_DEGRADED; // keep full outages rare
            }
            boolean deviceLevel = isDeviceLevel(type);
            Target target = deviceLevel
                    ? Target.of(deviceList.get(random.nextInt(deviceList.size())))
                    : Target.of(serviceList.get(random.nextInt(serviceList.size())));
            // Bias towards the last 24 hours so the trend chart has shape.
            long windowSeconds = random.nextInt(10) < 6 ? 22 * 3600 : 46 * 3600;
            Instant start = now.minus(Duration.ofHours(2)).minusSeconds((long) (random.nextDouble() * windowSeconds));
            resolvedEpisode(type, target, start);
        }
    }

    private void resolvedEpisode(String type, Target target, Instant start) {
        AlertRule rule = rulesByEventType.get(type);
        Event trigger = event(type, triggerSpec(type, 0), target, start);
        int repeats = random.nextInt(5);
        List<Event> occurrences = new ArrayList<>();
        Instant last = start;
        for (int i = 1; i <= repeats; i++) {
            last = last.plusSeconds(30 + random.nextInt(90));
            occurrences.add(event(type, triggerSpec(type, i), target, last));
        }

        Incident incident = newIncident(rule, trigger);
        List<Runnable> afterSave = new ArrayList<>();
        afterSave.add(() -> link(incident, trigger, IncidentEventRelation.TRIGGER, trigger.getOccurredAt()));
        occurrences.forEach(e -> afterSave.add(() -> link(incident, e, IncidentEventRelation.OCCURRENCE,
                e.getOccurredAt())));
        afterSave.add(() -> history(incident, null, HistoryAction.CREATED, null, rule.getIncidentSeverity().name(),
                "Incident created by rule \"%s\" from a %s event".formatted(rule.getName(), type), start));

        boolean autoResolved = rule.isAutoResolve() && random.nextDouble() < 0.55;
        Instant resolvedAt;
        if (autoResolved) {
            resolvedAt = last.plusSeconds(60 + random.nextInt(540));
            Event recovery = event(recoveryType(rule), recoverySpec(type), target, resolvedAt);
            afterSave.add(() -> link(incident, recovery, IncidentEventRelation.RECOVERY, resolvedAt));
            afterSave.add(() -> history(incident, null, HistoryAction.AUTO_RESOLVED, "OPEN", "RESOLVED",
                    "Automatically resolved after %s was received".formatted(recovery.getEventType()), resolvedAt));
            incident.restoreLifecycle(IncidentStatus.RESOLVED, null, 1 + repeats, last, null, null, resolvedAt,
                    "Automatically resolved: %s received from %s".formatted(recovery.getEventType(),
                            recovery.getSourceId()));
        } else {
            User responder = responder();
            User admin = users.get("admin@dispatch.local");
            Instant assignedAt = start.plusSeconds(60 + random.nextInt(300));
            Instant ackAt = assignedAt.plusSeconds(random.nextInt(180));
            boolean selfAssigned = random.nextBoolean();
            afterSave.add(() -> history(incident, selfAssigned ? responder : admin, HistoryAction.ASSIGNED, null,
                    responder.getName(), "Assigned to " + responder.getName(), assignedAt));
            afterSave.add(() -> history(incident, responder, HistoryAction.STATUS_CHANGED, "OPEN", "INVESTIGATING",
                    "Status changed from OPEN to INVESTIGATING", ackAt));

            if (random.nextDouble() < 0.15) {
                IncidentSeverity from = rule.getIncidentSeverity();
                IncidentSeverity to = from == IncidentSeverity.CRITICAL ? IncidentSeverity.HIGH
                        : IncidentSeverity.values()[from.ordinal() + 1];
                incident.changeSeverity(to);
                Instant changedAt = ackAt.plusSeconds(30 + random.nextInt(120));
                afterSave.add(() -> history(incident, responder, HistoryAction.SEVERITY_CHANGED, from.name(),
                        to.name(), "Severity changed from %s to %s".formatted(from, to), changedAt));
            }

            Instant cursor = ackAt;
            List<String> pool = SeedCatalog.COMMENTS.getOrDefault(type, List.of());
            int commentCount = Math.min(pool.size(), 1 + random.nextInt(2));
            for (int i = 0; i < commentCount; i++) {
                cursor = cursor.plusSeconds(120 + random.nextInt(600));
                String body = pool.get(i);
                Instant at = cursor;
                afterSave.add(() -> comment(incident, responder, body, at));
            }

            Instant recoveredAt = null;
            if (random.nextDouble() < 0.7) {
                cursor = cursor.plusSeconds(60 + random.nextInt(900));
                recoveredAt = cursor;
                Event recovery = event(recoveryType(rule), recoverySpec(type), target, cursor);
                Instant at = cursor;
                afterSave.add(() -> link(incident, recovery, IncidentEventRelation.RECOVERY, at));
                afterSave.add(() -> history(incident, null, HistoryAction.RECOVERY_DETECTED, null, null,
                        "Recovery signal %s received; left INVESTIGATING because %s".formatted(
                                recovery.getEventType(), rule.isAutoResolve()
                                        ? "it is already being investigated"
                                        : "rule \"" + rule.getName() + "\" requires operator confirmation"), at));
            }
            resolvedAt = cursor.plusSeconds(120 + random.nextInt(900));
            String note = SeedCatalog.RESOLUTIONS.get(type);
            afterSave.add(() -> history(incident, responder, HistoryAction.STATUS_CHANGED, "INVESTIGATING", "RESOLVED",
                    "Status changed from INVESTIGATING to RESOLVED: " + note, resolvedAtOf(incident)));
            incident.restoreLifecycle(IncidentStatus.RESOLVED, responder, 1 + repeats, last, ackAt, recoveredAt,
                    resolvedAt, note);
        }
        if (target.device() != null && EventTypes.DEVICE_OFFLINE.equals(type)) {
            offlineWindows.computeIfAbsent(target.device().getId(), k -> new ArrayList<>())
                    .add(new Instant[] {start.minusSeconds(60), resolvedAt});
        }
        incident.setTimestamps(start, resolvedAt);
        incidentRepository.save(incident);
        incidents++;
        afterSave.forEach(Runnable::run);
    }

    private static Instant resolvedAtOf(Incident incident) {
        return incident.getResolvedAt();
    }

    // ---- active incidents ------------------------------------------------------------------

    private void seedActiveIncidents() {
        User aravind = users.get("operator@dispatch.local");
        User priya = users.get("priya@dispatch.local");
        User marcus = users.get("marcus@dispatch.local");
        User sofia = users.get("sofia@dispatch.local");

        // CRITICAL
        Incident gatewayOffline = activeEpisode(EventTypes.DEVICE_OFFLINE, Target.of(devices.get("DEVICE-108")),
                minutesAgo(4), 5, 30, null, 0, List.of());
        markOffline(devices.get("DEVICE-108"), gatewayOffline.getCreatedAt());

        Incident encoderOffline = activeEpisode(EventTypes.DEVICE_OFFLINE, Target.of(devices.get("DEVICE-127")),
                minutesAgo(38), 7, 120, priya, 4, SeedCatalog.COMMENTS.get(EventTypes.DEVICE_OFFLINE).subList(0, 2));
        markOffline(devices.get("DEVICE-127"), encoderOffline.getCreatedAt());

        Incident notificationOutage = activeEpisode(EventTypes.SERVICE_DOWN,
                Target.of(services.get("Notification Service")), minutesAgo(22), 2, 90, marcus, 2,
                List.of("Push delivery stalled in every region. Failing over to the secondary queue cluster.",
                        "Failover to the secondary region completed. Verifying delivery before closing."));
        recoverWhileInvestigating(notificationOutage, Target.of(services.get("Notification Service")),
                minutesAgo(3));

        // HIGH
        activeEpisode(EventTypes.CPU_USAGE_HIGH, Target.of(devices.get("DEVICE-042")), minutesAgo(17), 8, 75,
                aravind, 3, SeedCatalog.COMMENTS.get(EventTypes.CPU_USAGE_HIGH).subList(0, 1));
        activeEpisode(EventTypes.ERROR_RATE_HIGH, Target.of(services.get("Content Delivery API")), minutesAgo(9), 2,
                120, null, 0, List.of());
        activeEpisode(EventTypes.SERVICE_DEGRADED, Target.of(services.get("Playback Service")), minutesAgo(52), 3,
                300, sofia, 6, SeedCatalog.COMMENTS.get(EventTypes.SERVICE_DEGRADED));
        declaredIncident(marcus, priya);

        // MEDIUM
        activeEpisode(EventTypes.MEMORY_USAGE_HIGH, Target.of(devices.get("DEVICE-076")), minutesAgo(26), 1, 240,
                null, 0, List.of());
        activeEpisode(EventTypes.LATENCY_HIGH, Target.of(services.get("Authentication Service")), minutesAgo(65), 3,
                420, priya, 7, SeedCatalog.COMMENTS.get(EventTypes.LATENCY_HIGH).subList(0, 1));
        activeEpisode(EventTypes.LATENCY_HIGH, Target.of(services.get("Content Delivery API")), minutesAgo(14), 1,
                180, null, 0, List.of());

        // LOW
        activeEpisode(EventTypes.DISK_USAGE_HIGH, Target.of(devices.get("DEVICE-144")), minutesAgo(200), 2, 1800,
                null, 0, List.of());
        Incident disk = activeEpisode(EventTypes.DISK_USAGE_HIGH, Target.of(devices.get("DEVICE-017")),
                minutesAgo(360), 4, 2400, null, 0, List.of());
        // Assigned but not yet acknowledged.
        disk.restoreLifecycle(IncidentStatus.OPEN, marcus, disk.getEventCount(), disk.getLastEventAt(), null, null,
                null, null);
        history(disk, users.get("admin@dispatch.local"), HistoryAction.ASSIGNED, null, marcus.getName(),
                "Assigned to " + marcus.getName(), disk.getCreatedAt().plusSeconds(900));
    }

    /**
     * An unresolved rule-based incident. With an assignee it is INVESTIGATING (acknowledged {@code ackMinutes}
     * after creation), otherwise OPEN.
     */
    private Incident activeEpisode(String type, Target target, Instant start, int repeats, int spacingSeconds,
            User assignee, int ackMinutes, List<String> comments) {
        AlertRule rule = rulesByEventType.get(type);
        Event trigger = event(type, triggerSpec(type, 0), target, start);
        List<Event> occurrences = new ArrayList<>();
        Instant last = start;
        for (int i = 1; i <= repeats; i++) {
            Instant next = last.plusSeconds(spacingSeconds / 2 + random.nextInt(Math.max(1, spacingSeconds)));
            if (next.isAfter(now.minusSeconds(5))) {
                break;
            }
            last = next;
            occurrences.add(event(type, triggerSpec(type, i), target, last));
        }

        Incident incident = newIncident(rule, trigger);
        Instant ackAt = assignee == null ? null : start.plus(Duration.ofMinutes(ackMinutes));
        incident.restoreLifecycle(assignee == null ? IncidentStatus.OPEN : IncidentStatus.INVESTIGATING, assignee,
                1 + occurrences.size(), last, ackAt, null, null, null);
        Instant updated = last;
        incident.setTimestamps(start, updated);
        incidentRepository.save(incident);
        incidents++;

        link(incident, trigger, IncidentEventRelation.TRIGGER, start);
        occurrences.forEach(e -> link(incident, e, IncidentEventRelation.OCCURRENCE, e.getOccurredAt()));
        history(incident, null, HistoryAction.CREATED, null, rule.getIncidentSeverity().name(),
                "Incident created by rule \"%s\" from a %s event".formatted(rule.getName(), type), start);
        if (assignee != null) {
            Instant assignedAt = ackAt.minusSeconds(45);
            history(incident, assignee, HistoryAction.ASSIGNED, null, assignee.getName(),
                    "Assigned to " + assignee.getName(), assignedAt);
            history(incident, assignee, HistoryAction.STATUS_CHANGED, "OPEN", "INVESTIGATING",
                    "Status changed from OPEN to INVESTIGATING", ackAt);
            Instant at = ackAt;
            for (String body : comments) {
                at = at.plusSeconds(90 + random.nextInt(240));
                if (at.isAfter(now)) {
                    at = now.minusSeconds(20);
                }
                comment(incident, assignee, body, at);
            }
        }
        trackImpact(target, start);
        return incident;
    }

    private void recoverWhileInvestigating(Incident incident, Target target, Instant at) {
        AlertRule rule = incident.getRule();
        Event recovery = event(recoveryType(rule), recoverySpec(rule.getEventType()), target, at);
        link(incident, recovery, IncidentEventRelation.RECOVERY, at);
        incident.markRecovered(at);
        history(incident, null, HistoryAction.RECOVERY_DETECTED, null, null,
                "Recovery signal %s received; left INVESTIGATING because rule \"%s\" requires operator confirmation"
                        .formatted(recovery.getEventType(), rule.getName()), at);
    }

    /** A customer-reported problem that no rule detected, declared by hand. */
    private void declaredIncident(User marcus, User priya) {
        MonitoredService playback = services.get("Playback Service");
        Instant created = minutesAgo(100);
        Incident incident = Incident.declare("Rebuffering reports from APAC subscribers",
                "Support has received 40+ reports of rebuffering on Android TV in India and Singapore since this "
                        + "morning. No alert has fired; investigating CDN edge performance in ap-south-1.",
                IncidentSeverity.HIGH, playback, null, marcus);
        incident.restoreLifecycle(IncidentStatus.INVESTIGATING, marcus, 0, null, created.plusSeconds(120), null,
                null, null);
        incident.setTimestamps(created, minutesAgo(31));
        incidentRepository.save(incident);
        incidents++;
        history(incident, marcus, HistoryAction.CREATED, null, "HIGH", "Incident declared manually by Marcus Chen",
                created);
        history(incident, marcus, HistoryAction.ASSIGNED, null, marcus.getName(), "Assigned to Marcus Chen",
                created.plusSeconds(60));
        history(incident, marcus, HistoryAction.STATUS_CHANGED, "OPEN", "INVESTIGATING",
                "Status changed from OPEN to INVESTIGATING", created.plusSeconds(120));
        comment(incident, marcus, "Ticket volume is still climbing. Pulling QoE data for ap-south-1.",
                created.plusSeconds(600));
        comment(incident, priya, "CDN logs show elevated origin fetch times from the Mumbai edge since 07:50.",
                created.plusSeconds(1900));
        comment(incident, marcus, "Opened a case with the CDN provider (#88231). Shifting 30% of APAC traffic to the "
                + "Singapore edge as a mitigation.", minutesAgo(31));
        trackImpact(Target.of(playback), created);
    }

    // ---- background telemetry --------------------------------------------------------------

    private void seedBackgroundEvents() {
        Instant start = now.minus(HISTORY);
        for (Device device : devices.values()) {
            Instant at = start.plusSeconds(random.nextInt(6000));
            while (at.isBefore(now.minusSeconds(30))) {
                if (!isOffline(device, at)) {
                    event(EventTypes.DEVICE_HEARTBEAT, new Spec(EventSeverity.INFO, "Heartbeat received",
                            Map.of("uptimeSeconds", 3600 + random.nextInt(2_000_000), "firmware",
                                    "4.2." + random.nextInt(6))), Target.of(device), at);
                }
                at = at.plusSeconds(5400 + random.nextInt(5400));
            }
        }
        List<Device> deviceList = new ArrayList<>(devices.values());
        for (int i = 0; i < 55; i++) {
            Device device = deviceList.get(random.nextInt(deviceList.size()));
            Instant at = randomTime();
            if (isOffline(device, at)) {
                continue;
            }
            int cpu = 78 + random.nextInt(12);
            event(EventTypes.CPU_USAGE_HIGH, new Spec(EventSeverity.LOW, "CPU usage at " + cpu
                    + "% (below alert threshold)", Map.of("cpu", cpu, "threshold", 90)), Target.of(device), at);
        }
        for (int i = 0; i < 30; i++) {
            Device device = deviceList.get(random.nextInt(deviceList.size()));
            event(EventTypes.CONFIG_RELOADED, new Spec(EventSeverity.INFO,
                    "Configuration reloaded (rev " + (1000 + random.nextInt(1500)) + ")",
                    Map.of("source", "config-service")), Target.of(device), randomTime());
        }
        for (MonitoredService service : services.values()) {
            int deployments = 2 + random.nextInt(3);
            for (int i = 0; i < deployments; i++) {
                String version = "%d.%d.%d".formatted(1 + random.nextInt(4), random.nextInt(30), random.nextInt(10));
                event(EventTypes.DEPLOYMENT_COMPLETED, new Spec(EventSeverity.INFO,
                        "Deployed %s v%s".formatted(service.slug(), version),
                        Map.of("version", version, "commit", "%07x".formatted(random.nextInt(0x10000000)),
                                "strategy", "rolling")), Target.of(service), randomTime());
            }
        }
        MonitoredService cdnApi = services.get("Content Delivery API");
        for (int i = 0; i < 8; i++) {
            event(EventTypes.CACHE_PURGED, new Spec(EventSeverity.INFO, "Edge cache purged for /catalog/*",
                    Map.of("objects", 200 + random.nextInt(9000))), Target.of(cdnApi), randomTime());
        }
        event(EventTypes.CERTIFICATE_RENEWED, new Spec(EventSeverity.INFO, "TLS certificate renewed for api.dispatch.local",
                Map.of("expiresInDays", 90)), Target.of(services.get("Authentication Service")), randomTime());
    }

    private void finalizeHealth() {
        healthService.reconcileAll();
        // Point "status changed at" to when the current problem began rather than the moment of seeding.
        for (MonitoredService service : services.values()) {
            if (service.getStatus() != ServiceStatus.HEALTHY && impactedSince.containsKey(service.getId())) {
                service.setStatusChangedAt(impactedSince.get(service.getId()));
            }
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    private Incident newIncident(AlertRule rule, Event trigger) {
        EventFacts facts = EventFacts.of(trigger, codec.decode(trigger.getMetadata()));
        return Incident.fromRule(rule, trigger, IncidentTitles.render(rule, trigger, facts),
                IncidentFingerprint.of(rule, facts));
    }

    private Event event(String type, Spec spec, Target target, Instant at) {
        boolean deviceLevel = target.device() != null;
        Event event = new Event(type, spec.severity(), deviceLevel ? SourceType.DEVICE : SourceType.SERVICE,
                deviceLevel ? target.device().getIdentifier() : target.service().slug(), target.service(),
                target.device(), spec.message(), codec.encode(spec.metadata()), at,
                at.plusMillis(40 + random.nextInt(800)));
        events++;
        return eventRepository.save(event);
    }

    private void link(Incident incident, Event event, IncidentEventRelation relation, Instant at) {
        linkRepository.save(new IncidentEvent(incident, event, relation, at));
    }

    private void history(Incident incident, User actor, HistoryAction action, String from, String to, String message,
            Instant at) {
        historyRepository.save(new IncidentHistory(incident, actor, action, from, to, message, at));
    }

    private void comment(Incident incident, User author, String body, Instant at) {
        commentRepository.save(new IncidentComment(incident, author, body, at));
        history(incident, author, HistoryAction.COMMENT_ADDED, null, null, author.getName() + " added a comment", at);
    }

    private void markOffline(Device device, Instant since) {
        device.changeStatus(DeviceStatus.OFFLINE);
        device.setLastSeenAt(since.minusSeconds(45));
        offlineWindows.computeIfAbsent(device.getId(), k -> new ArrayList<>())
                .add(new Instant[] {since.minusSeconds(60), now.plusSeconds(3600)});
    }

    private void trackImpact(Target target, Instant since) {
        impactedSince.merge(target.service().getId(), since, (a, b) -> a.isBefore(b) ? a : b);
    }

    private boolean isOffline(Device device, Instant at) {
        return offlineWindows.getOrDefault(device.getId(), List.of()).stream()
                .anyMatch(window -> !at.isBefore(window[0]) && at.isBefore(window[1]));
    }

    private User responder() {
        List<User> responders = users.values().stream()
                .filter(u -> u.isActive() && u.getRole() == Role.OPERATOR)
                .toList();
        return responders.get(random.nextInt(responders.size()));
    }

    private Instant minutesAgo(long minutes) {
        return now.minus(Duration.ofMinutes(minutes));
    }

    private Instant randomTime() {
        return now.minus(HISTORY).plusSeconds((long) (random.nextDouble() * (HISTORY.toSeconds() - 60)));
    }

    private static boolean isDeviceLevel(String type) {
        return switch (type) {
            case EventTypes.DEVICE_OFFLINE, EventTypes.CPU_USAGE_HIGH, EventTypes.MEMORY_USAGE_HIGH,
                 EventTypes.DISK_USAGE_HIGH -> true;
            default -> false;
        };
    }

    private static String recoveryType(AlertRule rule) {
        return rule.getRecoveryEventType();
    }

    private Spec triggerSpec(String type, int repeat) {
        boolean again = repeat > 0;
        return switch (type) {
            case EventTypes.DEVICE_OFFLINE -> again
                    ? new Spec(EventSeverity.CRITICAL, "Device still unreachable (%d missed heartbeats)"
                            .formatted(3 + repeat * 3), Map.of("missedHeartbeats", 3 + repeat * 3))
                    : new Spec(EventSeverity.CRITICAL, "Device stopped responding",
                            Map.of("missedHeartbeats", 3, "lastSeenSecondsAgo", 45));
            case EventTypes.CPU_USAGE_HIGH -> {
                int cpu = 91 + random.nextInt(9);
                yield new Spec(EventSeverity.HIGH, (again ? "CPU usage still elevated at " : "CPU usage reached ")
                        + cpu + "%", Map.of("cpu", cpu, "threshold", 90, "cores", 16));
            }
            case EventTypes.MEMORY_USAGE_HIGH -> {
                int memory = 90 + random.nextInt(8);
                yield new Spec(EventSeverity.MEDIUM, "Memory usage at " + memory + "% of 32 GiB",
                        Map.of("memory", memory, "usedGiB", Math.round(memory * 3.2) / 10.0));
            }
            case EventTypes.DISK_USAGE_HIGH -> {
                int disk = 86 + random.nextInt(10);
                yield new Spec(EventSeverity.MEDIUM, "Disk usage on /var/lib at " + disk + "%",
                        Map.of("disk", disk, "mount", "/var/lib"));
            }
            case EventTypes.ERROR_RATE_HIGH -> {
                double rate = Math.round((5.5 + random.nextDouble() * 10) * 10) / 10.0;
                yield new Spec(EventSeverity.HIGH, "5xx error rate at " + rate + "% over the last 5 minutes",
                        Map.of("errorRate", rate, "window", "5m", "threshold", 5));
            }
            case EventTypes.LATENCY_HIGH -> {
                int p95 = 1600 + random.nextInt(2600);
                yield new Spec(EventSeverity.MEDIUM, "p95 latency " + p95 + " ms (threshold 1500 ms)",
                        Map.of("p95LatencyMs", p95, "p50LatencyMs", p95 / 3));
            }
            case EventTypes.SERVICE_DEGRADED -> new Spec(EventSeverity.HIGH, "Health checks failing in 1 of 3 regions",
                    Map.of("failingRegions", "eu-west-1", "checks", 12));
            case EventTypes.SERVICE_DOWN -> new Spec(EventSeverity.CRITICAL, "Health checks failing in all regions",
                    Map.of("failingRegions", "all", "checks", 12));
            default -> throw new IllegalArgumentException("No seed spec for " + type);
        };
    }

    private Spec recoverySpec(String type) {
        return switch (type) {
            case EventTypes.DEVICE_OFFLINE -> new Spec(EventSeverity.INFO, "Device reconnected",
                    Map.of("downtimeSeconds", 120 + random.nextInt(1500)));
            case EventTypes.CPU_USAGE_HIGH -> {
                int cpu = 35 + random.nextInt(25);
                yield new Spec(EventSeverity.INFO, "CPU usage returned to normal (" + cpu + "%)", Map.of("cpu", cpu));
            }
            case EventTypes.MEMORY_USAGE_HIGH -> new Spec(EventSeverity.INFO, "Memory usage back to normal",
                    Map.of("memory", 50 + random.nextInt(20)));
            case EventTypes.DISK_USAGE_HIGH -> new Spec(EventSeverity.INFO, "Disk usage reduced after log rotation",
                    Map.of("disk", 55 + random.nextInt(15)));
            case EventTypes.ERROR_RATE_HIGH -> new Spec(EventSeverity.INFO, "Error rate back to baseline",
                    Map.of("errorRate", 0.4));
            case EventTypes.LATENCY_HIGH -> new Spec(EventSeverity.INFO, "p95 latency recovered",
                    Map.of("p95LatencyMs", 200 + random.nextInt(400)));
            case EventTypes.SERVICE_DEGRADED, EventTypes.SERVICE_DOWN -> new Spec(EventSeverity.INFO,
                    "All health checks passing", Map.of("checks", 12));
            default -> throw new IllegalArgumentException("No recovery spec for " + type);
        };
    }
}
