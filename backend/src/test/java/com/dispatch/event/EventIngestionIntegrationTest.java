package com.dispatch.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.dispatch.device.DeviceRepository;
import com.dispatch.device.DeviceStatus;
import com.dispatch.event.dto.EventIngestRequest;
import com.dispatch.incident.IncidentRepository;
import com.dispatch.incident.IncidentStatus;
import com.dispatch.service.MonitoredServiceRepository;
import com.dispatch.service.ServiceStatus;
import com.dispatch.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class EventIngestionIntegrationTest extends IntegrationTest {

    @Autowired
    EventIngestionService ingestionService;
    @Autowired
    IncidentRepository incidentRepository;
    @Autowired
    DeviceRepository deviceRepository;
    @Autowired
    MonitoredServiceRepository serviceRepository;

    @Test
    void anOutageIsDetectedDeduplicatedAndRecovered() throws Exception {
        // 1. The device goes offline: one CRITICAL incident, device OFFLINE, service DEGRADED.
        String created = ingest(deviceEvent("DEVICE_OFFLINE", "CRITICAL", "DEVICE-108"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.event.sourceId").value("DEVICE-108"))
                .andExpect(jsonPath("$.event.service.name").value("Device Gateway"))
                .andExpect(jsonPath("$.incidentActions[0].type").value("CREATED"))
                .andExpect(jsonPath("$.incidentActions[0].title").value("DEVICE-108 is offline"))
                .andExpect(jsonPath("$.incidentActions[0].severity").value("CRITICAL"))
                .andReturn().getResponse().getContentAsString();
        long incidentId = incidentIdFrom(created);
        assertThat(deviceRepository.findById(data.device108.getId()).orElseThrow().getStatus())
                .isEqualTo(DeviceStatus.OFFLINE);
        assertThat(serviceRepository.findById(data.gateway.getId()).orElseThrow().getStatus())
                .isEqualTo(ServiceStatus.DEGRADED);

        // 2. The same problem keeps reporting: absorbed by the existing incident.
        for (int i = 0; i < 3; i++) {
            ingest(deviceEvent("DEVICE_OFFLINE", "CRITICAL", "device-108"))
                    .andExpect(jsonPath("$.incidentActions[0].type").value("DEDUPLICATED"))
                    .andExpect(jsonPath("$.incidentActions[0].incidentId").value(incidentId));
        }
        assertThat(incidentRepository.count()).isEqualTo(1);
        String token = adminToken();
        mvc.perform(bearer(get("/api/v1/incidents/{id}", incidentId), token))
                .andExpect(jsonPath("$.eventCount").value(4))
                .andExpect(jsonPath("$.status").value("OPEN"));

        // 3. Recovery: nobody had started working on it, so it resolves automatically.
        ingest(deviceEvent("DEVICE_RECOVERED", "INFO", "DEVICE-108"))
                .andExpect(jsonPath("$.incidentActions[0].type").value("AUTO_RESOLVED"))
                .andExpect(jsonPath("$.incidentActions[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.event.incidents[0].relation").value("RECOVERY"));

        mvc.perform(bearer(get("/api/v1/incidents/{id}", incidentId), token))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        mvc.perform(bearer(get("/api/v1/incidents/{id}/history", incidentId), token))
                .andExpect(jsonPath("$[*].action").value(org.hamcrest.Matchers.contains("CREATED", "AUTO_RESOLVED")));
        mvc.perform(bearer(get("/api/v1/incidents/{id}/events", incidentId), token))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.triggers").value(1))
                .andExpect(jsonPath("$.occurrences").value(3))
                .andExpect(jsonPath("$.recoveries").value(1));
        assertThat(deviceRepository.findById(data.device108.getId()).orElseThrow().getStatus())
                .isEqualTo(DeviceStatus.ONLINE);
        assertThat(serviceRepository.findById(data.gateway.getId()).orElseThrow().getStatus())
                .isEqualTo(ServiceStatus.HEALTHY);
    }

    @Test
    void aRecoveryDoesNotCloseAnIncidentSomeoneIsInvestigating() throws Exception {
        long incidentId = incidentIdFrom(ingest(deviceEvent("DEVICE_OFFLINE", "CRITICAL", "DEVICE-108"))
                .andReturn().getResponse().getContentAsString());
        String token = operatorToken();
        mvc.perform(bearer(put("/api/v1/incidents/{id}/status", incidentId), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INVESTIGATING\"}"))
                .andExpect(status().isOk());

        ingest(deviceEvent("DEVICE_RECOVERED", "INFO", "DEVICE-108"))
                .andExpect(jsonPath("$.incidentActions[0].type").value("RECOVERY_DETECTED"))
                .andExpect(jsonPath("$.incidentActions[0].status").value("INVESTIGATING"));

        mvc.perform(bearer(get("/api/v1/incidents/{id}", incidentId), token))
                .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.recoveredAt").isNotEmpty());
        // Health reflects the recovery immediately even though the incident stays open.
        assertThat(serviceRepository.findById(data.gateway.getId()).orElseThrow().getStatus())
                .isEqualTo(ServiceStatus.HEALTHY);
    }

    @Test
    void theProblemReturningAfterResolutionOpensANewIncident() throws Exception {
        long first = incidentIdFrom(ingest(deviceEvent("DEVICE_OFFLINE", "CRITICAL", "DEVICE-108"))
                .andReturn().getResponse().getContentAsString());
        ingest(deviceEvent("DEVICE_RECOVERED", "INFO", "DEVICE-108"));
        long second = incidentIdFrom(ingest(deviceEvent("DEVICE_OFFLINE", "CRITICAL", "DEVICE-108"))
                .andExpect(jsonPath("$.incidentActions[0].type").value("CREATED"))
                .andReturn().getResponse().getContentAsString());

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void metricRulesOnlyFireAboveTheirThreshold() throws Exception {
        ingest(deviceEvent("CPU_USAGE_HIGH", "LOW", "DEVICE-042", "{\"cpu\": 85}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentActions").isEmpty());
        ingest(deviceEvent("CPU_USAGE_HIGH", "HIGH", "DEVICE-042", "{\"cpu\": 94, \"threshold\": 90}"))
                .andExpect(jsonPath("$.incidentActions[0].type").value("CREATED"))
                .andExpect(jsonPath("$.incidentActions[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.event.metadata.cpu").value(94));
    }

    @Test
    void serviceOutagesNeedAnOperatorToConfirmRecovery() throws Exception {
        String outage = """
                {"eventType":"SERVICE_DOWN","severity":"CRITICAL","sourceType":"SERVICE",
                 "sourceId":"notification-service","serviceId":%d,"message":"All health checks failing"}
                """.formatted(data.notifications.getId());
        ingest(outage).andExpect(jsonPath("$.incidentActions[0].title").value("Notification Service is down"));
        assertThat(serviceRepository.findById(data.notifications.getId()).orElseThrow().getStatus())
                .isEqualTo(ServiceStatus.DOWN);

        ingest(outage.replace("SERVICE_DOWN", "SERVICE_RECOVERED").replace("CRITICAL", "INFO"))
                .andExpect(jsonPath("$.incidentActions[0].type").value("RECOVERY_DETECTED"))
                .andExpect(jsonPath("$.incidentActions[0].status").value("OPEN"));
    }

    @Test
    void concurrentDuplicatesProduceExactlyOneIncident() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return ingestionService.ingest(new EventIngestRequest("DEVICE_OFFLINE", EventSeverity.CRITICAL,
                            SourceType.DEVICE, "DEVICE-108", null, "Device stopped responding", Map.of(), null));
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(incidentRepository.findAll()).singleElement().satisfies(incident -> {
            assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
            assertThat(incident.getEventCount()).isEqualTo(threads);
        });
    }

    @Test
    void concurrentEventsForOneDeviceNeverOverwriteItsStatus() throws Exception {
        // One OFFLINE event racing unrelated events from the same device: the device must end up OFFLINE.
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                String type = i == 0 ? "DEVICE_OFFLINE" : "CONFIG_RELOADED";
                EventSeverity severity = i == 0 ? EventSeverity.CRITICAL : EventSeverity.INFO;
                results.add(pool.submit(() -> {
                    start.await();
                    return ingestionService.ingest(new EventIngestRequest(type, severity, SourceType.DEVICE,
                            "DEVICE-108", null, type, Map.of(), null));
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(deviceRepository.findById(data.device108.getId()).orElseThrow().getStatus())
                .isEqualTo(DeviceStatus.OFFLINE);
        assertThat(serviceRepository.findById(data.gateway.getId()).orElseThrow().getStatus())
                .isEqualTo(ServiceStatus.DEGRADED);
    }

    @Test
    void invalidEventsAreRejectedWithFieldLevelErrors() throws Exception {
        ingest("{\"eventType\":\"cpu high\",\"severity\":\"HIGH\",\"sourceType\":\"DEVICE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'eventType')].message")
                        .value("must be UPPER_SNAKE_CASE, e.g. DEVICE_OFFLINE"))
                .andExpect(jsonPath("$.errors[?(@.field == 'sourceId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'message')]").exists());

        ingest(deviceEvent("DEVICE_OFFLINE", "SEVERE", "DEVICE-108"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Allowed values")));
    }

    @Test
    void eventsMustReferenceKnownSources() throws Exception {
        ingest(deviceEvent("DEVICE_OFFLINE", "CRITICAL", "DEVICE-999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sourceId"));

        ingest("""
                {"eventType":"SERVICE_DOWN","severity":"CRITICAL","sourceType":"SERVICE","sourceId":"x","message":"m"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("serviceId"));

        String future = Instant.now().plusSeconds(3600).toString();
        ingest("""
                {"eventType":"DEVICE_OFFLINE","severity":"CRITICAL","sourceType":"DEVICE","sourceId":"DEVICE-108",
                 "message":"m","timestamp":"%s"}
                """.formatted(future))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("timestamp"));
    }

    @Test
    void administratorsMaySubmitEventsWithTheirToken() throws Exception {
        mvc.perform(bearer(post("/api/v1/events"), adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceEvent("DEVICE_HEARTBEAT", "INFO", "DEVICE-042")))
                .andExpect(status().isCreated());
    }

    @Test
    void theEventStreamIsKeysetPaginated() throws Exception {
        for (int i = 0; i < 5; i++) {
            ingest(deviceEvent("DEVICE_HEARTBEAT", "INFO", "DEVICE-042"));
        }
        String token = adminToken();
        String firstPage = mvc.perform(bearer(get("/api/v1/events?limit=3"), token))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = com.jayway.jsonpath.JsonPath.read(firstPage, "$.nextCursor");

        mvc.perform(bearer(get("/api/v1/events").param("limit", "3").param("cursor", cursor), token))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(bearer(get("/api/v1/events").param("cursor", "garbage!"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }
}
