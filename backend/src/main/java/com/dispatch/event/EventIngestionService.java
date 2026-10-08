package com.dispatch.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.dispatch.common.error.FieldValidationException;
import com.dispatch.device.Device;
import com.dispatch.device.DeviceRepository;
import com.dispatch.event.dto.EventIngestRequest;
import com.dispatch.event.dto.EventResponse;
import com.dispatch.event.dto.IngestionResponse;
import com.dispatch.health.HealthService;
import com.dispatch.incident.IncidentAutomationService;
import com.dispatch.incident.IncidentEventRelation;
import com.dispatch.incident.dto.EventIncidentLink;
import com.dispatch.incident.dto.IncidentAction;
import com.dispatch.rule.evaluation.EventFacts;
import com.dispatch.service.MonitoredService;
import com.dispatch.service.MonitoredServiceRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The event pipeline. One transaction per event:
 * <ol>
 *   <li>validate and resolve the source (device / service)</li>
 *   <li>persist the event</li>
 *   <li>evaluate alert rules → open, de-duplicate or recover incidents</li>
 *   <li>re-derive device and service health</li>
 *   <li>publish domain events, which are broadcast over WebSocket <em>after commit</em></li>
 * </ol>
 * Everything commits or rolls back together, so clients never see an incident whose event was not stored.
 * Transient concurrency failures (an operator editing the same incident, a deadlock victim) are retried
 * with a fresh transaction instead of dropping the event.
 */
@Service
public class EventIngestionService {

    static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
    private static final int MAX_METADATA_BYTES = 16 * 1024;
    private static final int MAX_ATTEMPTS = 3;
    private static final Logger log = LoggerFactory.getLogger(EventIngestionService.class);

    private final EventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final EventMetadataCodec metadataCodec;
    private final IncidentAutomationService automation;
    private final HealthService healthService;
    private final EventMapper mapper;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public EventIngestionService(EventRepository eventRepository, DeviceRepository deviceRepository,
            MonitoredServiceRepository serviceRepository, EventMetadataCodec metadataCodec,
            IncidentAutomationService automation, HealthService healthService, EventMapper mapper,
            ApplicationEventPublisher publisher, PlatformTransactionManager transactionManager, Clock clock) {
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.serviceRepository = serviceRepository;
        this.metadataCodec = metadataCodec;
        this.automation = automation;
        this.healthService = healthService;
        this.mapper = mapper;
        this.publisher = publisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public IngestionResponse ingest(EventIngestRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> ingestOnce(request));
            } catch (ConcurrencyFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.info("Retrying ingestion of {} from {} after a concurrent update (attempt {})",
                        request.eventType(), request.sourceId(), attempt);
            }
        }
    }

    private IngestionResponse ingestOnce(EventIngestRequest request) {
        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant occurredAt = request.timestamp() == null
                ? receivedAt
                : request.timestamp().truncatedTo(ChronoUnit.MICROS);
        if (occurredAt.isAfter(receivedAt.plus(MAX_FUTURE_SKEW))) {
            throw new FieldValidationException("timestamp", "must not be more than 5 minutes in the future");
        }

        MonitoredService service = null;
        if (request.serviceId() != null) {
            service = serviceRepository.findById(request.serviceId())
                    .orElseThrow(() -> new FieldValidationException("serviceId",
                            "service " + request.serviceId() + " does not exist"));
        }

        Device device = null;
        String sourceId = request.sourceId().trim();
        switch (request.sourceType()) {
            case DEVICE -> {
                String identifier = sourceId;
                // Row lock: events for one device are processed one at a time (no lost status updates).
                device = deviceRepository.lockByIdentifier(identifier)
                        .orElseThrow(() -> new FieldValidationException("sourceId",
                                "no device is registered with identifier " + identifier));
                if (service != null && !service.getId().equals(device.getService().getId())) {
                    throw new FieldValidationException("serviceId", "device %s belongs to service %d (%s)"
                            .formatted(device.getIdentifier(), device.getService().getId(),
                                    device.getService().getName()));
                }
                service = device.getService();
                sourceId = device.getIdentifier();
            }
            case SERVICE -> {
                if (service == null) {
                    throw new FieldValidationException("serviceId", "is required when sourceType is SERVICE");
                }
            }
            case EXTERNAL -> {
                // External integrations may optionally be attributed to a service.
            }
        }

        String metadata = metadataCodec.encode(request.metadata());
        if (metadata.length() > MAX_METADATA_BYTES) {
            throw new FieldValidationException("metadata", "must be at most 16 KB when serialized");
        }

        Event event = eventRepository.save(new Event(request.eventType(), request.severity(),
                request.sourceType(), sourceId, service, device, request.message().trim(), metadata, occurredAt,
                receivedAt));
        if (device != null && !EventTypes.DEVICE_OFFLINE.equals(event.getEventType())) {
            device.recordSeen(occurredAt);
        }

        List<IncidentAction> actions = automation.process(event, EventFacts.of(event, request.metadata()));
        healthService.reconcileAfterEvent(event, actions);

        List<EventIncidentLink> links = actions.stream()
                .map(action -> new EventIncidentLink(action.incidentId(), action.key(), action.title(),
                        action.severity(), action.status(), relationOf(action.type())))
                .toList();
        EventResponse response = mapper.toResponse(event, links);
        publisher.publishEvent(new EventRecordedEvent(response));
        return new IngestionResponse(response, actions);
    }

    private static IncidentEventRelation relationOf(IncidentAction.Type type) {
        return switch (type) {
            case CREATED -> IncidentEventRelation.TRIGGER;
            case DEDUPLICATED, RECURRED -> IncidentEventRelation.OCCURRENCE;
            case RECOVERY_DETECTED, AUTO_RESOLVED -> IncidentEventRelation.RECOVERY;
        };
    }
}
