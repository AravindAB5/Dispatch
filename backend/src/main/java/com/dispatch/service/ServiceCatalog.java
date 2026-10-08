package com.dispatch.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dispatch.common.error.ConflictException;
import com.dispatch.common.error.NotFoundException;
import com.dispatch.device.DeviceRepository;
import com.dispatch.device.DeviceStatus;
import com.dispatch.incident.IncidentRepository;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.incident.IncidentStatus;
import com.dispatch.service.dto.ServiceRequest;
import com.dispatch.service.dto.ServiceResponse;
import com.dispatch.service.dto.ServiceResponse.DeviceCounts;
import com.dispatch.service.dto.ServiceResponse.IncidentCounts;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registry of monitored services, enriched with device and active incident counts. */
@Service
@Transactional(readOnly = true)
public class ServiceCatalog {

    private final MonitoredServiceRepository serviceRepository;
    private final DeviceRepository deviceRepository;
    private final IncidentRepository incidentRepository;

    public ServiceCatalog(MonitoredServiceRepository serviceRepository, DeviceRepository deviceRepository,
            IncidentRepository incidentRepository) {
        this.serviceRepository = serviceRepository;
        this.deviceRepository = deviceRepository;
        this.incidentRepository = incidentRepository;
    }

    /** Three queries in total regardless of the number of services. */
    public List<ServiceResponse> list() {
        Map<Long, DeviceCounts> devices = deviceCounts();
        Map<Long, IncidentCounts> incidents = incidentCounts();
        return serviceRepository.findAllByOrderByNameAsc().stream()
                .map(s -> ServiceResponse.from(s, devices.getOrDefault(s.getId(), DeviceCounts.EMPTY),
                        incidents.getOrDefault(s.getId(), IncidentCounts.EMPTY)))
                .toList();
    }

    public ServiceResponse get(Long id) {
        MonitoredService service = require(id);
        return ServiceResponse.from(service, deviceCounts().getOrDefault(id, DeviceCounts.EMPTY),
                incidentCounts().getOrDefault(id, IncidentCounts.EMPTY));
    }

    public MonitoredService require(Long id) {
        return serviceRepository.findById(id).orElseThrow(() -> NotFoundException.of("Service", id));
    }

    @Transactional
    public ServiceResponse create(ServiceRequest request) {
        String name = request.name().trim();
        if (serviceRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("SERVICE_NAME_TAKEN", "A service named \"" + name + "\" already exists");
        }
        MonitoredService service = serviceRepository.save(new MonitoredService(name, blankToNull(request.description()),
                request.environment(), blankToNull(request.ownerTeam())));
        return ServiceResponse.from(service, DeviceCounts.EMPTY, IncidentCounts.EMPTY);
    }

    @Transactional
    public ServiceResponse update(Long id, ServiceRequest request) {
        MonitoredService service = require(id);
        String name = request.name().trim();
        if (serviceRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("SERVICE_NAME_TAKEN", "A service named \"" + name + "\" already exists");
        }
        service.setName(name);
        service.setDescription(blankToNull(request.description()));
        service.setEnvironment(request.environment());
        service.setOwnerTeam(blankToNull(request.ownerTeam()));
        return get(id);
    }

    private Map<Long, DeviceCounts> deviceCounts() {
        Map<Long, long[]> raw = new HashMap<>();
        for (Object[] row : deviceRepository.countByServiceAndStatusRaw()) {
            long[] counts = raw.computeIfAbsent((Long) row[0], k -> new long[3]);
            counts[((DeviceStatus) row[1]).ordinal()] += (Long) row[2];
        }
        Map<Long, DeviceCounts> result = new HashMap<>();
        raw.forEach((serviceId, c) -> result.put(serviceId, new DeviceCounts(c[0] + c[1] + c[2],
                c[DeviceStatus.ONLINE.ordinal()], c[DeviceStatus.DEGRADED.ordinal()],
                c[DeviceStatus.OFFLINE.ordinal()])));
        return result;
    }

    private Map<Long, IncidentCounts> incidentCounts() {
        Map<Long, long[]> raw = new HashMap<>();
        for (Object[] row : incidentRepository.countByServiceAndSeverityRaw(IncidentStatus.ACTIVE)) {
            long[] counts = raw.computeIfAbsent((Long) row[0], k -> new long[4]);
            counts[((IncidentSeverity) row[1]).ordinal()] += (Long) row[2];
        }
        Map<Long, IncidentCounts> result = new HashMap<>();
        raw.forEach((serviceId, c) -> result.put(serviceId, new IncidentCounts(c[0] + c[1] + c[2] + c[3],
                c[IncidentSeverity.CRITICAL.ordinal()], c[IncidentSeverity.HIGH.ordinal()],
                c[IncidentSeverity.MEDIUM.ordinal()], c[IncidentSeverity.LOW.ordinal()])));
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
