package com.dispatch.simulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.dispatch.device.DeviceRepository;
import com.dispatch.device.DeviceStatus;
import com.dispatch.event.EventIngestionService;
import com.dispatch.event.EventSeverity;
import com.dispatch.event.EventTypes;
import com.dispatch.event.SourceType;
import com.dispatch.event.dto.EventIngestRequest;
import com.dispatch.service.MonitoredServiceRepository;
import com.dispatch.simulator.SimulatedProblem.Kind;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Generates a realistic stream of telemetry: heartbeats, sub-threshold warnings, deployments, and
 * problems (CPU spikes, devices going offline, error-rate spikes, outages) that later recover.
 * Events go through {@link EventIngestionService} exactly like externally submitted events, so the
 * simulator exercises the real pipeline without containing any production logic itself.
 */
@Component
@ConditionalOnProperty(prefix = "dispatch.simulator", name = "enabled", havingValue = "true")
public class EventSimulator {

    private static final Logger log = LoggerFactory.getLogger(EventSimulator.class);
    private static final Duration TARGET_REFRESH = Duration.ofSeconds(60);
    private static final Duration FORGET_UNRECOVERED_AFTER = Duration.ofMinutes(10);

    private final EventIngestionService ingestion;
    private final DeviceRepository deviceRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final SimulatorProperties properties;
    private final Clock clock;
    private final SplittableRandom random = new SplittableRandom();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("event-simulator").daemon().factory());
    private final AtomicLong eventsGenerated = new AtomicLong();

    // Mutated only on the single simulator thread; read under the monitor for status().
    private final List<SimulatedProblem> problems = new ArrayList<>();
    private List<DeviceTarget> devices = List.of();
    private List<ServiceTarget> services = List.of();
    private Instant targetsLoadedAt = Instant.EPOCH;

    private ScheduledFuture<?> task;
    private volatile Instant startedAt;

    record DeviceTarget(String identifier, Long serviceId, DeviceStatus status) {
    }

    record ServiceTarget(Long id, String name, String slug) {
    }

    public EventSimulator(EventIngestionService ingestion, DeviceRepository deviceRepository,
            MonitoredServiceRepository serviceRepository, SimulatorProperties properties, Clock clock) {
        this.ingestion = ingestion;
        this.deviceRepository = deviceRepository;
        this.serviceRepository = serviceRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoStart() {
        if (properties.autoStart()) {
            start();
        }
    }

    public synchronized SimulatorStatus start() {
        if (task == null) {
            task = executor.scheduleWithFixedDelay(this::tickSafely, 500, properties.interval().toMillis(),
                    TimeUnit.MILLISECONDS);
            startedAt = clock.instant();
            log.info("Event simulator started (interval {} ms)", properties.interval().toMillis());
        }
        return status();
    }

    public synchronized SimulatorStatus stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
            startedAt = null;
            log.info("Event simulator stopped after {} events", eventsGenerated.get());
        }
        return status();
    }

    public synchronized SimulatorStatus status() {
        List<String> active;
        synchronized (problems) {
            active = problems.stream().map(SimulatedProblem::describe).toList();
        }
        return new SimulatorStatus(task != null, startedAt, properties.interval().toMillis(), eventsGenerated.get(),
                properties.maxConcurrentProblems(), active);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException e) {
            log.warn("Simulator tick failed: {}", e.getMessage());
        }
    }

    void tick() {
        Instant now = clock.instant();
        refreshTargets(now);
        if (devices.isEmpty()) {
            return;
        }

        synchronized (problems) {
            // 1. Problems whose time has come recover (or are forgotten if they need an operator).
            Iterator<SimulatedProblem> it = problems.iterator();
            while (it.hasNext()) {
                SimulatedProblem problem = it.next();
                if (problem.recoverAt != null && !now.isBefore(problem.recoverAt)) {
                    emit(problem.recovery(random, now));
                    it.remove();
                } else if (problem.recoverAt == null && now.isAfter(problem.forgetAt)) {
                    it.remove();
                }
            }

            // 2. Ongoing problems keep reporting, which exercises de-duplication.
            if (!problems.isEmpty() && random.nextDouble() < 0.25) {
                emit(problems.get(random.nextInt(problems.size())).repeat(random));
                return;
            }
        }

        // 3. Something new happens.
        double roll = random.nextDouble() * 100;
        boolean capacity;
        synchronized (problems) {
            capacity = problems.size() < properties.maxConcurrentProblems();
        }
        if (roll < 34 || !capacity && roll < 60) {
            heartbeat();
        } else if (roll < 44 || !capacity && roll < 80) {
            cpuWarning();
        } else if (roll < 54 || !capacity) {
            informational();
        } else if (roll < 66) {
            startProblem(Kind.CPU, now);
        } else if (roll < 73) {
            startProblem(Kind.MEMORY, now);
        } else if (roll < 76) {
            startProblem(Kind.DISK, now);
        } else if (roll < 82.5) {
            startProblem(Kind.OFFLINE, now);
        } else if (roll < 88) {
            startProblem(Kind.ERROR_RATE, now);
        } else if (roll < 94) {
            startProblem(Kind.LATENCY, now);
        } else if (roll < 98.5) {
            startProblem(Kind.SERVICE_DEGRADED, now);
        } else {
            startProblem(Kind.SERVICE_DOWN, now);
        }
    }

    private void startProblem(Kind kind, Instant now) {
        Set<String> busy = new HashSet<>();
        synchronized (problems) {
            problems.forEach(p -> busy.add(p.sourceId));
        }
        SimulatedProblem problem;
        if (kind.deviceLevel) {
            List<DeviceTarget> candidates = devices.stream()
                    .filter(d -> d.status() != DeviceStatus.OFFLINE && !busy.contains(d.identifier()))
                    .toList();
            if (candidates.isEmpty()) {
                heartbeat();
                return;
            }
            DeviceTarget target = candidates.get(random.nextInt(candidates.size()));
            problem = new SimulatedProblem(kind, target.identifier(), target.serviceId(), target.identifier(), now,
                    recoveryTime(kind, now), now.plus(FORGET_UNRECOVERED_AFTER));
        } else {
            List<ServiceTarget> candidates = services.stream().filter(s -> !busy.contains(s.slug())).toList();
            if (candidates.isEmpty()) {
                informational();
                return;
            }
            ServiceTarget target = candidates.get(random.nextInt(candidates.size()));
            problem = new SimulatedProblem(kind, target.slug(), target.id(), target.name(), now,
                    recoveryTime(kind, now), now.plus(FORGET_UNRECOVERED_AFTER));
        }
        synchronized (problems) {
            problems.add(problem);
        }
        emit(problem.trigger(random));
    }

    private Instant recoveryTime(Kind kind, Instant now) {
        int[] seconds = switch (kind) {
            case CPU -> new int[] {20, 60};
            case MEMORY -> new int[] {40, 120};
            case DISK -> new int[] {90, 240};
            case OFFLINE -> new int[] {45, 150};
            case ERROR_RATE -> new int[] {40, 120};
            case LATENCY -> new int[] {30, 90};
            case SERVICE_DEGRADED -> new int[] {60, 150};
            case SERVICE_DOWN -> new int[] {60, 120};
        };
        // Some outages do not fix themselves and wait for an operator.
        boolean persistent = (kind == Kind.OFFLINE || kind == Kind.SERVICE_DEGRADED) && random.nextDouble() < 0.15;
        return persistent ? null : now.plusSeconds(random.nextInt(seconds[0], seconds[1] + 1));
    }

    /** Devices that are online and not in the middle of a simulated problem. */
    private List<DeviceTarget> idleOnlineDevices() {
        Set<String> busy = new HashSet<>();
        synchronized (problems) {
            problems.forEach(p -> busy.add(p.sourceId));
        }
        return devices.stream()
                .filter(d -> d.status() != DeviceStatus.OFFLINE && !busy.contains(d.identifier()))
                .toList();
    }

    private void heartbeat() {
        // Occasionally bring back a device left offline (e.g. by seed data or an outage an operator resolved).
        if (random.nextDouble() < 0.08 && reconnectOfflineDevice()) {
            return;
        }
        List<DeviceTarget> online = idleOnlineDevices();
        if (online.isEmpty()) {
            return;
        }
        DeviceTarget device = online.get(random.nextInt(online.size()));
        emit(new EventIngestRequest(EventTypes.DEVICE_HEARTBEAT, EventSeverity.INFO, SourceType.DEVICE,
                device.identifier(), null, "Heartbeat received",
                Map.of("uptimeSeconds", random.nextInt(3_600, 2_000_000), "firmware", "4.2." + random.nextInt(0, 6)),
                null));
    }

    private boolean reconnectOfflineDevice() {
        Set<String> busy = new HashSet<>();
        synchronized (problems) {
            problems.forEach(p -> busy.add(p.sourceId));
        }
        List<DeviceTarget> offline = devices.stream()
                .filter(d -> d.status() == DeviceStatus.OFFLINE && !busy.contains(d.identifier()))
                .toList();
        if (offline.isEmpty()) {
            return false;
        }
        DeviceTarget device = offline.get(random.nextInt(offline.size()));
        emit(new EventIngestRequest(EventTypes.DEVICE_RECOVERED, EventSeverity.INFO, SourceType.DEVICE,
                device.identifier(), null, "Device reconnected after maintenance", Map.of("reason", "power-cycle"),
                null));
        targetsLoadedAt = Instant.EPOCH; // refresh statuses on the next tick
        return true;
    }

    private void cpuWarning() {
        List<DeviceTarget> candidates = idleOnlineDevices();
        if (candidates.isEmpty()) {
            return;
        }
        DeviceTarget device = candidates.get(random.nextInt(candidates.size()));
        int cpu = random.nextInt(78, 90);
        emit(new EventIngestRequest(EventTypes.CPU_USAGE_HIGH, EventSeverity.LOW, SourceType.DEVICE,
                device.identifier(), null, "CPU usage at " + cpu + "% (below alert threshold)",
                Map.of("cpu", cpu, "threshold", 90), null));
    }

    private void informational() {
        ServiceTarget service = services.get(random.nextInt(services.size()));
        int pick = random.nextInt(4);
        EventIngestRequest request = switch (pick) {
            case 0 -> new EventIngestRequest(EventTypes.DEPLOYMENT_COMPLETED, EventSeverity.INFO, SourceType.SERVICE,
                    service.slug(), service.id(),
                    "Deployed %s v%d.%d.%d".formatted(service.slug(), random.nextInt(1, 5), random.nextInt(0, 30),
                            random.nextInt(0, 10)),
                    Map.of("commit", "%07x".formatted(random.nextInt(0x10000000)), "strategy", "rolling"),
                    null);
            case 1 -> new EventIngestRequest(EventTypes.CACHE_PURGED, EventSeverity.INFO, SourceType.SERVICE,
                    service.slug(), service.id(), "Edge cache purged for /catalog/*",
                    Map.of("objects", random.nextInt(200, 9_000)), null);
            default -> {
                List<DeviceTarget> candidates = idleOnlineDevices();
                DeviceTarget device = candidates.isEmpty()
                        ? devices.get(random.nextInt(devices.size()))
                        : candidates.get(random.nextInt(candidates.size()));
                yield new EventIngestRequest(EventTypes.CONFIG_RELOADED, EventSeverity.INFO, SourceType.DEVICE,
                        device.identifier(), null, "Configuration reloaded (rev " + random.nextInt(1_000, 2_500) + ")",
                        Map.of("source", "config-service"), null);
            }
        };
        emit(request);
    }

    private void emit(EventIngestRequest request) {
        ingestion.ingest(request);
        eventsGenerated.incrementAndGet();
    }

    private void refreshTargets(Instant now) {
        if (Duration.between(targetsLoadedAt, now).compareTo(TARGET_REFRESH) < 0 && !devices.isEmpty()) {
            return;
        }
        devices = deviceRepository.findAllByOrderByIdentifierAsc().stream()
                .map(d -> new DeviceTarget(d.getIdentifier(), d.getService().getId(), d.getStatus()))
                .toList();
        services = serviceRepository.findAllByOrderByNameAsc().stream()
                .map(s -> new ServiceTarget(s.getId(), s.getName(), s.slug()))
                .toList();
        targetsLoadedAt = now;
    }
}
