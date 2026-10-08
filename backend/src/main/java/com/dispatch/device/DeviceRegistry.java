package com.dispatch.device;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dispatch.common.error.ConflictException;
import com.dispatch.common.error.FieldValidationException;
import com.dispatch.common.error.NotFoundException;
import com.dispatch.common.web.PageResponse;
import com.dispatch.device.dto.DeviceRequest;
import com.dispatch.device.dto.DeviceResponse;
import com.dispatch.health.HealthService;
import com.dispatch.incident.IncidentRepository;
import com.dispatch.incident.IncidentStatus;
import com.dispatch.service.MonitoredService;
import com.dispatch.service.MonitoredServiceRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registry of monitored devices. */
@Service
@Transactional(readOnly = true)
public class DeviceRegistry {

    private final DeviceRepository deviceRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final HealthService healthService;

    public DeviceRegistry(DeviceRepository deviceRepository, MonitoredServiceRepository serviceRepository,
            IncidentRepository incidentRepository, HealthService healthService) {
        this.deviceRepository = deviceRepository;
        this.serviceRepository = serviceRepository;
        this.incidentRepository = incidentRepository;
        this.healthService = healthService;
    }

    public PageResponse<DeviceResponse> search(Long serviceId, List<DeviceStatus> statuses, String query, int page,
            int size) {
        Page<Device> result = deviceRepository.findAll(DeviceSpecifications.matching(serviceId, statuses, query),
                PageRequest.of(page, size, Sort.by("identifier")));
        Map<Long, Long> active = activeIncidentCounts(result.getContent().stream().map(Device::getId).toList());
        List<DeviceResponse> content = result.getContent().stream()
                .map(d -> DeviceResponse.from(d, active.getOrDefault(d.getId(), 0L)))
                .toList();
        return PageResponse.of(content, result);
    }

    public DeviceResponse get(Long id) {
        Device device = require(id);
        return DeviceResponse.from(device, activeIncidentCounts(List.of(id)).getOrDefault(id, 0L));
    }

    public Device require(Long id) {
        return deviceRepository.findWithServiceById(id).orElseThrow(() -> NotFoundException.of("Device", id));
    }

    @Transactional
    public DeviceResponse create(DeviceRequest request) {
        String identifier = Device.normalizeIdentifier(request.identifier());
        if (deviceRepository.existsByIdentifierIgnoreCase(identifier)) {
            throw new ConflictException("DEVICE_IDENTIFIER_TAKEN", "A device with identifier " + identifier
                    + " already exists");
        }
        Device device = new Device(identifier, request.name().trim(), request.deviceType().trim(),
                requireService(request.serviceId()), blankToNull(request.location()));
        return DeviceResponse.from(deviceRepository.save(device), 0);
    }

    /** The identifier is immutable: event producers reference devices by it. */
    @Transactional
    public DeviceResponse update(Long id, DeviceRequest request) {
        Device device = require(id);
        if (!device.getIdentifier().equalsIgnoreCase(request.identifier().trim())) {
            throw new FieldValidationException("identifier", "cannot be changed once a device is registered");
        }
        device.setName(request.name().trim());
        device.setDeviceType(request.deviceType().trim());
        device.setLocation(blankToNull(request.location()));
        MonitoredService previous = device.getService();
        MonitoredService next = requireService(request.serviceId());
        device.setService(next);
        if (!previous.getId().equals(next.getId())) {
            // Both services' derived health depends on this device (e.g. an offline device moving away).
            healthService.reconcile(previous, null);
            healthService.reconcile(next, null);
        }
        return get(id);
    }

    private MonitoredService requireService(Long serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new FieldValidationException("serviceId", "service " + serviceId
                        + " does not exist"));
    }

    private Map<Long, Long> activeIncidentCounts(Collection<Long> deviceIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (deviceIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : incidentRepository.countByDeviceRaw(deviceIds, IncidentStatus.ACTIVE)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
