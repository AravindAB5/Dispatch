package com.dispatch.health;

import static com.dispatch.support.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import com.dispatch.device.Device;
import com.dispatch.device.DeviceRepository;
import com.dispatch.device.DeviceStatus;
import com.dispatch.event.EventTypes;
import com.dispatch.incident.IncidentRepository;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.service.MonitoredService;
import com.dispatch.service.MonitoredServiceRepository;
import com.dispatch.service.ServiceStatus;
import com.dispatch.service.ServiceStatusChangedEvent;
import com.dispatch.support.Fixtures;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    DeviceRepository deviceRepository;
    @Mock
    MonitoredServiceRepository serviceRepository;
    @Mock
    IncidentRepository incidentRepository;
    @Mock
    ApplicationEventPublisher publisher;
    @Mock
    EntityManager entityManager;

    private HealthService health;
    private final MonitoredService gateway = Fixtures.service(3, "Device Gateway");
    private final Device device = Fixtures.device(8, "DEVICE-108", gateway);

    @BeforeEach
    void setUp() {
        health = new HealthService(deviceRepository, serviceRepository, incidentRepository, entityManager, publisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(deviceRepository.countByServiceId(3L)).thenReturn(6L);
        lenient().when(deviceRepository.countByServiceIdAndStatus(3L, DeviceStatus.OFFLINE)).thenReturn(0L);
    }

    private static List<Object[]> signals(Object[]... rows) {
        return Arrays.asList(rows);
    }

    private void givenSignals(Object[]... rows) {
        when(incidentRepository.findImpactingSignalsForServiceRaw(eq(3L), any())).thenReturn(signals(rows));
    }

    @Test
    void serviceIsHealthyWithoutImpactingIncidents() {
        givenSignals();
        assertThat(health.deriveServiceStatus(3L)).isEqualTo(ServiceStatus.HEALTHY);
    }

    @Test
    void serviceLevelCriticalIncidentMeansDown() {
        givenSignals(new Object[] {IncidentSeverity.CRITICAL, null});
        assertThat(health.deriveServiceStatus(3L)).isEqualTo(ServiceStatus.DOWN);
    }

    @Test
    void serviceLevelHighIncidentMeansDegraded() {
        givenSignals(new Object[] {IncidentSeverity.HIGH, null});
        assertThat(health.deriveServiceStatus(3L)).isEqualTo(ServiceStatus.DEGRADED);
    }

    @Test
    void aSingleHotDeviceDoesNotDegradeTheWholeService() {
        givenSignals(new Object[] {IncidentSeverity.HIGH, 8L}, new Object[] {IncidentSeverity.MEDIUM, null});
        assertThat(health.deriveServiceStatus(3L)).isEqualTo(ServiceStatus.HEALTHY);
    }

    @Test
    void aCriticalDeviceIncidentDegradesTheService() {
        givenSignals(new Object[] {IncidentSeverity.CRITICAL, 8L});
        assertThat(health.deriveServiceStatus(3L)).isEqualTo(ServiceStatus.DEGRADED);
    }

    @Test
    void anOfflineDeviceDegradesAndAnOfflineMajorityTakesTheServiceDown() {
        givenSignals();
        when(deviceRepository.countByServiceIdAndStatus(3L, DeviceStatus.OFFLINE)).thenReturn(1L);
        assertThat(health.deriveServiceStatus(3L)).isEqualTo(ServiceStatus.DEGRADED);

        when(deviceRepository.countByServiceIdAndStatus(3L, DeviceStatus.OFFLINE)).thenReturn(4L);
        assertThat(health.deriveServiceStatus(3L)).isEqualTo(ServiceStatus.DOWN);
    }

    @Test
    void deviceConnectivityFollowsOfflineAndRecoveryEvents() {
        assertThat(health.deriveDeviceStatus(device, EventTypes.DEVICE_OFFLINE)).isEqualTo(DeviceStatus.OFFLINE);

        device.changeStatus(DeviceStatus.OFFLINE);
        assertThat(health.deriveDeviceStatus(device, EventTypes.CPU_USAGE_HIGH)).isEqualTo(DeviceStatus.OFFLINE);

        when(incidentRepository.findImpactingSeveritiesForDevice(eq(8L), any())).thenReturn(List.of());
        assertThat(health.deriveDeviceStatus(device, EventTypes.DEVICE_RECOVERED)).isEqualTo(DeviceStatus.ONLINE);
    }

    @Test
    void deviceIsDegradedWhileItHasAnImpactingIncidentOfMediumOrAbove() {
        when(incidentRepository.findImpactingSeveritiesForDevice(eq(8L), any()))
                .thenReturn(List.of(IncidentSeverity.LOW));
        assertThat(health.deriveDeviceStatus(device, null)).isEqualTo(DeviceStatus.ONLINE);

        when(incidentRepository.findImpactingSeveritiesForDevice(eq(8L), any()))
                .thenReturn(List.of(IncidentSeverity.LOW, IncidentSeverity.MEDIUM));
        assertThat(health.deriveDeviceStatus(device, null)).isEqualTo(DeviceStatus.DEGRADED);
    }

    @Test
    void publishesOnlyWhenTheServiceStatusActuallyChanges() {
        givenSignals(new Object[] {IncidentSeverity.HIGH, null});

        health.reconcile(gateway, null);
        assertThat(gateway.getStatus()).isEqualTo(ServiceStatus.DEGRADED);
        assertThat(gateway.getStatusChangedAt()).isEqualTo(NOW);
        verify(publisher).publishEvent(any(ServiceStatusChangedEvent.class));

        org.mockito.Mockito.clearInvocations(publisher);
        health.reconcile(gateway, null);
        verify(publisher, never()).publishEvent(any());
    }
}
