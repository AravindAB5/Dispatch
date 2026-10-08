package com.dispatch.health;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.dispatch.device.Device;
import com.dispatch.device.DeviceRepository;
import com.dispatch.device.DeviceStatus;
import com.dispatch.device.DeviceStatusChangedEvent;
import com.dispatch.event.Event;
import com.dispatch.event.EventTypes;
import com.dispatch.incident.IncidentRepository;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.incident.IncidentStatus;
import com.dispatch.incident.dto.IncidentAction;
import com.dispatch.service.MonitoredService;
import com.dispatch.service.MonitoredServiceRepository;
import com.dispatch.service.ServiceStatus;
import com.dispatch.service.ServiceStatusChangedEvent;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives device and service health so it always agrees with the incident list.
 *
 * <p><b>Device</b>
 * <ul>
 *   <li>OFFLINE after a DEVICE_OFFLINE event, until DEVICE_RECOVERED or a heartbeat arrives.</li>
 *   <li>Otherwise DEGRADED while it has an impacting incident of MEDIUM severity or above, else ONLINE.</li>
 * </ul>
 * <p><b>Service</b>
 * <ul>
 *   <li>DOWN when it has an impacting CRITICAL service-level incident, or more than half its devices are
 *       offline.</li>
 *   <li>DEGRADED when it has an impacting HIGH service-level incident, a CRITICAL device incident, or any
 *       offline device. (A single node running hot does not degrade the whole service.)</li>
 *   <li>HEALTHY otherwise.</li>
 * </ul>
 * "Impacting" means unresolved and not flagged as recovered: once the source reports recovery, health
 * reflects it immediately even if an operator has not closed the incident yet.
 */
@Service
public class HealthService {

    private final DeviceRepository deviceRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public HealthService(DeviceRepository deviceRepository, MonitoredServiceRepository serviceRepository,
            IncidentRepository incidentRepository, EntityManager entityManager, ApplicationEventPublisher publisher,
            Clock clock) {
        this.deviceRepository = deviceRepository;
        this.serviceRepository = serviceRepository;
        this.incidentRepository = incidentRepository;
        this.entityManager = entityManager;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Called once per ingested event, after alert processing. Only recomputes what could have changed:
     * the reporting device on connectivity events, and the devices/services whose set of active
     * incidents changed.
     */
    @Transactional
    public void reconcileAfterEvent(Event event, List<IncidentAction> actions) {
        Set<Long> deviceIds = new LinkedHashSet<>();
        Set<Long> serviceIds = new LinkedHashSet<>();
        for (IncidentAction action : actions) {
            // A de-duplicated repeat does not change which incidents impact health; everything else can.
            if (action.type() != IncidentAction.Type.DEDUPLICATED) {
                addIfPresent(deviceIds, action.deviceId());
                addIfPresent(serviceIds, action.serviceId());
            }
        }

        // The reporting device was loaded with a row lock by the ingestion pipeline, so its state is current.
        Device reporting = event.getDevice();
        if (reporting != null && (isConnectivityEvent(event.getEventType()) || deviceIds.contains(reporting.getId()))) {
            if (reconcileDevice(reporting, event.getEventType())) {
                serviceIds.add(reporting.getService().getId());
            }
            deviceIds.remove(reporting.getId());
        }
        for (Long deviceId : deviceIds) {
            deviceRepository.findById(deviceId).ifPresent(device -> {
                entityManager.refresh(device);
                if (reconcileDevice(device, null)) {
                    serviceIds.add(device.getService().getId());
                }
            });
        }
        for (Long serviceId : serviceIds) {
            serviceRepository.findById(serviceId).ifPresent(this::reconcileService);
        }
    }

    /** Called after an operator changes an incident (status, severity, ...). */
    @Transactional
    public void reconcile(MonitoredService service, Device device) {
        if (device != null) {
            // Re-read so a status committed by a concurrent event is not overwritten with a stale value.
            entityManager.refresh(device);
            reconcileDevice(device, null);
        }
        MonitoredService target = service != null ? service : device != null ? device.getService() : null;
        if (target != null) {
            reconcileService(target);
        }
    }

    /** Recomputes everything (used after bulk seeding). */
    @Transactional
    public void reconcileAll() {
        deviceRepository.findAll().forEach(device -> reconcileDevice(device, null));
        serviceRepository.findAll().forEach(this::reconcileService);
    }

    boolean reconcileDevice(Device device, String eventType) {
        DeviceStatus previous = device.getStatus();
        DeviceStatus next = deriveDeviceStatus(device, eventType);
        if (device.changeStatus(next)) {
            publisher.publishEvent(DeviceStatusChangedEvent.of(device, previous));
            return true;
        }
        return false;
    }

    DeviceStatus deriveDeviceStatus(Device device, String eventType) {
        boolean offline;
        if (EventTypes.DEVICE_OFFLINE.equals(eventType)) {
            offline = true;
        } else if (EventTypes.DEVICE_RECOVERED.equals(eventType) || EventTypes.DEVICE_HEARTBEAT.equals(eventType)) {
            offline = false;
        } else {
            offline = device.getStatus() == DeviceStatus.OFFLINE;
        }
        if (offline) {
            return DeviceStatus.OFFLINE;
        }
        boolean impaired = incidentRepository.findImpactingSeveritiesForDevice(device.getId(), IncidentStatus.ACTIVE)
                .stream()
                .anyMatch(severity -> severity.isAtLeast(IncidentSeverity.MEDIUM));
        return impaired ? DeviceStatus.DEGRADED : DeviceStatus.ONLINE;
    }

    void reconcileService(MonitoredService service) {
        // Serialise health derivation per service: lock the row and re-read it, so two transactions deriving
        // from different snapshots cannot overwrite each other. Always the last lock taken, so no lock cycles.
        entityManager.refresh(service, LockModeType.PESSIMISTIC_WRITE);
        ServiceStatus previous = service.getStatus();
        ServiceStatus next = deriveServiceStatus(service.getId());
        if (service.changeStatus(next, clock.instant())) {
            publisher.publishEvent(new ServiceStatusChangedEvent(service.getId(), service.getName(), next, previous,
                    service.getStatusChangedAt()));
        }
    }

    ServiceStatus deriveServiceStatus(Long serviceId) {
        List<Object[]> signals =
                incidentRepository.findImpactingSignalsForServiceRaw(serviceId, IncidentStatus.ACTIVE);
        long totalDevices = deviceRepository.countByServiceId(serviceId);
        long offlineDevices = deviceRepository.countByServiceIdAndStatus(serviceId, DeviceStatus.OFFLINE);

        boolean serviceLevelCritical = false;
        boolean degraded = offlineDevices > 0;
        for (Object[] row : signals) {
            IncidentSeverity severity = (IncidentSeverity) row[0];
            boolean serviceLevel = row[1] == null;
            if (serviceLevel && severity == IncidentSeverity.CRITICAL) {
                serviceLevelCritical = true;
            }
            if (serviceLevel ? severity.isAtLeast(IncidentSeverity.HIGH) : severity == IncidentSeverity.CRITICAL) {
                degraded = true;
            }
        }
        if (serviceLevelCritical || (totalDevices > 0 && offlineDevices * 2 > totalDevices)) {
            return ServiceStatus.DOWN;
        }
        return degraded ? ServiceStatus.DEGRADED : ServiceStatus.HEALTHY;
    }

    private static boolean isConnectivityEvent(String eventType) {
        return EventTypes.DEVICE_OFFLINE.equals(eventType)
                || EventTypes.DEVICE_RECOVERED.equals(eventType)
                || EventTypes.DEVICE_HEARTBEAT.equals(eventType);
    }

    private static void addIfPresent(Set<Long> ids, Long id) {
        if (id != null) {
            ids.add(id);
        }
    }
}
