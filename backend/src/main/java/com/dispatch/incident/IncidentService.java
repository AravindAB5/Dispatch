package com.dispatch.incident;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.dispatch.common.error.BusinessRuleException;
import com.dispatch.common.error.ConflictException;
import com.dispatch.common.error.FieldValidationException;
import com.dispatch.common.error.NotFoundException;
import com.dispatch.device.Device;
import com.dispatch.device.DeviceRepository;
import com.dispatch.health.HealthService;
import com.dispatch.incident.IncidentChangedEvent.ChangeType;
import com.dispatch.incident.dto.CommentResponse;
import com.dispatch.incident.dto.IncidentDetail;
import com.dispatch.incident.dto.IncidentRequests;
import com.dispatch.service.MonitoredService;
import com.dispatch.service.MonitoredServiceRepository;
import com.dispatch.user.User;
import com.dispatch.user.UserService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operator commands on incidents. Every change is validated by the {@link Incident} aggregate, recorded
 * in the incident history, reflected in service/device health, and broadcast after commit.
 */
@Service
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentCommentRepository commentRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final DeviceRepository deviceRepository;
    private final UserService userService;
    private final IncidentHistoryRecorder history;
    private final FingerprintLock fingerprintLock;
    private final HealthService healthService;
    private final IncidentMapper mapper;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public IncidentService(IncidentRepository incidentRepository, IncidentCommentRepository commentRepository,
            MonitoredServiceRepository serviceRepository, DeviceRepository deviceRepository,
            UserService userService, IncidentHistoryRecorder history, FingerprintLock fingerprintLock,
            HealthService healthService, IncidentMapper mapper, ApplicationEventPublisher publisher, Clock clock) {
        this.incidentRepository = incidentRepository;
        this.commentRepository = commentRepository;
        this.serviceRepository = serviceRepository;
        this.deviceRepository = deviceRepository;
        this.userService = userService;
        this.history = history;
        this.fingerprintLock = fingerprintLock;
        this.healthService = healthService;
        this.mapper = mapper;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Opens an incident by hand, e.g. for a customer-reported problem no rule detected. */
    public IncidentDetail declare(IncidentRequests.Declare request, Long actorId) {
        User actor = userService.require(actorId);
        Device device = null;
        MonitoredService service = null;
        if (request.deviceId() != null) {
            device = deviceRepository.findWithServiceById(request.deviceId())
                    .orElseThrow(() -> new FieldValidationException("deviceId",
                            "device " + request.deviceId() + " does not exist"));
            service = device.getService();
        }
        if (request.serviceId() != null) {
            MonitoredService requested = serviceRepository.findById(request.serviceId())
                    .orElseThrow(() -> new FieldValidationException("serviceId",
                            "service " + request.serviceId() + " does not exist"));
            if (service != null && !service.getId().equals(requested.getId())) {
                throw new FieldValidationException("deviceId",
                        "device %s does not belong to %s".formatted(device.getIdentifier(), requested.getName()));
            }
            service = requested;
        }

        Incident incident = Incident.declare(request.title().trim(), blankToNull(request.description()),
                request.severity(), service, device, actor);
        incidentRepository.save(incident);
        history.record(incident, actor, HistoryAction.CREATED, null, incident.getSeverity().name(),
                "Incident declared manually by " + actor.getName());
        healthService.reconcile(service, device);
        publish(ChangeType.CREATED, incident);
        return mapper.toDetail(incident);
    }

    public IncidentDetail assign(Long incidentId, Long assigneeId, Long actorId) {
        Incident incident = require(incidentId);
        User actor = userService.require(actorId);
        User previous = incident.getAssignee();
        User assignee = null;
        if (assigneeId != null) {
            assignee = userService.require(assigneeId);
            if (!assignee.isActive()) {
                throw new BusinessRuleException("ASSIGNEE_INACTIVE",
                        assignee.getName() + " is deactivated and cannot be assigned incidents");
            }
        }
        if (Objects.equals(id(previous), id(assignee))) {
            return mapper.toDetail(incident);
        }

        incident.assignTo(assignee);
        incident.touch(now());
        if (assignee == null) {
            history.record(incident, actor, HistoryAction.UNASSIGNED, previous.getName(), null,
                    "Unassigned from " + previous.getName());
        } else {
            history.record(incident, actor, HistoryAction.ASSIGNED, previous == null ? null : previous.getName(),
                    assignee.getName(), previous == null
                            ? "Assigned to " + assignee.getName()
                            : "Reassigned from %s to %s".formatted(previous.getName(), assignee.getName()));
        }
        publish(ChangeType.UPDATED, incident);
        return mapper.toDetail(incident);
    }

    public IncidentDetail changeStatus(Long incidentId, IncidentStatus target, String note, Long actorId) {
        Incident incident = require(incidentId);
        User actor = userService.require(actorId);
        IncidentStatus previous = incident.getStatus();

        if (previous == IncidentStatus.RESOLVED && target == IncidentStatus.OPEN && incident.getFingerprint() != null) {
            // Reopening must not create a second active incident for the same problem.
            fingerprintLock.acquire(incident.getFingerprint());
            incidentRepository.findActiveByFingerprint(incident.getFingerprint())
                    .filter(other -> !other.getId().equals(incident.getId()))
                    .ifPresent(other -> {
                        throw new ConflictException("ACTIVE_DUPLICATE_EXISTS",
                                "Cannot reopen: %s is already tracking this problem".formatted(other.getKey()));
                    });
        }

        incident.transitionTo(target, now(), note);
        incident.touch(now());

        if (previous == IncidentStatus.RESOLVED) {
            history.record(incident, actor, HistoryAction.REOPENED, previous.name(), target.name(),
                    "Incident reopened" + suffix(note));
        } else {
            history.record(incident, actor, HistoryAction.STATUS_CHANGED, previous.name(), target.name(),
                    "Status changed from %s to %s%s".formatted(previous, target, suffix(note)));
        }
        healthService.reconcile(incident.getService(), incident.getDevice());
        publish(target == IncidentStatus.RESOLVED ? ChangeType.RESOLVED : ChangeType.UPDATED, incident);
        return mapper.toDetail(incident);
    }

    public IncidentDetail changeSeverity(Long incidentId, IncidentSeverity severity, Long actorId) {
        Incident incident = require(incidentId);
        User actor = userService.require(actorId);
        IncidentSeverity previous = incident.getSeverity();
        if (previous == severity) {
            return mapper.toDetail(incident);
        }
        incident.changeSeverity(severity);
        incident.touch(now());
        history.record(incident, actor, HistoryAction.SEVERITY_CHANGED, previous.name(), severity.name(),
                "Severity changed from %s to %s".formatted(previous, severity));
        healthService.reconcile(incident.getService(), incident.getDevice());
        publish(ChangeType.UPDATED, incident);
        return mapper.toDetail(incident);
    }

    public CommentResponse addComment(Long incidentId, String body, Long actorId) {
        Incident incident = require(incidentId);
        User actor = userService.require(actorId);
        Instant now = now();
        IncidentComment comment = commentRepository.save(new IncidentComment(incident, actor, body.trim(), now));
        history.record(incident, actor, HistoryAction.COMMENT_ADDED, null, null, actor.getName() + " added a comment");
        incident.touch(now);
        publish(ChangeType.UPDATED, incident);
        return CommentResponse.from(comment);
    }

    private Incident require(Long id) {
        return incidentRepository.findDetailedById(id).orElseThrow(() -> NotFoundException.of("Incident", id));
    }

    private void publish(ChangeType type, Incident incident) {
        publisher.publishEvent(new IncidentChangedEvent(type, mapper.toSummary(incident)));
    }

    private Instant now() {
        return clock.instant();
    }

    private static Long id(User user) {
        return user == null ? null : user.getId();
    }

    private static String suffix(String note) {
        if (note == null || note.isBlank()) {
            return "";
        }
        String trimmed = note.trim();
        return ": " + (trimmed.length() > 300 ? trimmed.substring(0, 299) + "…" : trimmed);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
