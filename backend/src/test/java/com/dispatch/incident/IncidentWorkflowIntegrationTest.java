package com.dispatch.incident;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dispatch.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class IncidentWorkflowIntegrationTest extends IntegrationTest {

    private long openOfflineIncident() throws Exception {
        return incidentIdFrom(ingest(deviceEvent("DEVICE_OFFLINE", "CRITICAL", "DEVICE-108"))
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions command(String token, String path, long id, String json) throws Exception {
        return mvc.perform(bearer(put("/api/v1/incidents/{id}/" + path, id), token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void anOperatorTakesAnIncidentFromDetectionToResolution() throws Exception {
        long id = openOfflineIncident();
        String token = operatorToken();

        command(token, "assignee", id, "{\"assigneeId\":%d}".formatted(data.operator.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.name").value("Test Operator"));
        command(token, "status", id, "{\"status\":\"INVESTIGATING\"}")
                .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.acknowledgedAt").isNotEmpty());
        mvc.perform(bearer(post("/api/v1/incidents/{id}/comments", id), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Switch port 14 is down. Dispatching on-site tech.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.name").value("Test Operator"));
        command(token, "severity", id, "{\"severity\":\"HIGH\"}")
                .andExpect(jsonPath("$.severity").value("HIGH"));
        command(token, "status", id, "{\"status\":\"RESOLVED\",\"note\":\"Replaced faulty SFP module\"}")
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNote").value("Replaced faulty SFP module"));

        mvc.perform(bearer(get("/api/v1/incidents/{id}/history", id), token))
                .andExpect(jsonPath("$[*].action").value(contains("CREATED", "ASSIGNED", "STATUS_CHANGED",
                        "COMMENT_ADDED", "SEVERITY_CHANGED", "STATUS_CHANGED")))
                .andExpect(jsonPath("$[0].actor").isEmpty())
                .andExpect(jsonPath("$[1].actor.name").value("Test Operator"))
                .andExpect(jsonPath("$[5].message")
                        .value("Status changed from INVESTIGATING to RESOLVED: Replaced faulty SFP module"));
        mvc.perform(bearer(get("/api/v1/incidents/{id}/comments", id), token))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void invalidTransitionsAndEditsToResolvedIncidentsAreRejected() throws Exception {
        long id = openOfflineIncident();
        String token = operatorToken();
        command(token, "status", id, "{\"status\":\"RESOLVED\"}").andExpect(status().isOk());

        command(token, "status", id, "{\"status\":\"INVESTIGATING\"}")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        command(token, "severity", id, "{\"severity\":\"LOW\"}")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("INCIDENT_RESOLVED"));
        command(token, "assignee", id, "{\"assigneeId\":%d}".formatted(data.inactive.getId()))
                .andExpect(status().is(422));
    }

    @Test
    void reopeningIsBlockedWhileANewerIncidentTracksTheSameProblem() throws Exception {
        long first = openOfflineIncident();
        String token = operatorToken();
        command(token, "status", first, "{\"status\":\"RESOLVED\"}").andExpect(status().isOk());
        long second = openOfflineIncident();

        command(token, "status", first, "{\"status\":\"OPEN\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_DUPLICATE_EXISTS"))
                .andExpect(jsonPath("$.detail").value("Cannot reopen: INC-%d is already tracking this problem"
                        .formatted(second)));

        command(token, "status", second, "{\"status\":\"RESOLVED\"}").andExpect(status().isOk());
        command(token, "status", first, "{\"status\":\"OPEN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").isEmpty());
    }

    @Test
    void operatorsCanDeclareIncidentsManually() throws Exception {
        String token = operatorToken();
        mvc.perform(bearer(post("/api/v1/incidents"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Customers report slow logins","severity":"MEDIUM",
                                 "serviceId":%d,"description":"Reported by support"}
                                """.formatted(data.gateway.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.automated").value(false))
                .andExpect(jsonPath("$.createdBy.name").value("Test Operator"))
                .andExpect(jsonPath("$.service.name").value("Device Gateway"));

        mvc.perform(bearer(post("/api/v1/incidents"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"severity\":\"URGENT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theIncidentListSupportsFilteringSearchSortingAndPaging() throws Exception {
        openOfflineIncident();
        ingest(deviceEvent("CPU_USAGE_HIGH", "HIGH", "DEVICE-042", "{\"cpu\": 97}"));
        String token = operatorToken();

        mvc.perform(bearer(get("/api/v1/incidents").param("severity", "CRITICAL"), token))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].device.identifier").value("DEVICE-108"));
        mvc.perform(bearer(get("/api/v1/incidents").param("q", "device-042"), token))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("High CPU usage on DEVICE-042"));
        mvc.perform(bearer(get("/api/v1/incidents").param("sort", "severity"), token))
                .andExpect(jsonPath("$.content[*].severity").value(contains("CRITICAL", "HIGH")));
        mvc.perform(bearer(get("/api/v1/incidents").param("sort", "oldest").param("size", "1"), token))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].severity").value("CRITICAL"));
        mvc.perform(bearer(get("/api/v1/incidents").param("unassigned", "true")
                        .param("status", "OPEN", "INVESTIGATING"), token))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(bearer(get("/api/v1/incidents").param("page", "30000000").param("size", "100"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(bearer(get("/api/v1/incidents").param("sort", "loudest"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        mvc.perform(bearer(get("/api/v1/incidents").param("status", "OPEN", "NOPE"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.endsWith(
                        "Allowed values: OPEN, INVESTIGATING, RESOLVED.")));
    }

    @Test
    void dashboardSummaryReflectsActiveIncidents() throws Exception {
        openOfflineIncident();
        ingest(deviceEvent("CPU_USAGE_HIGH", "HIGH", "DEVICE-042", "{\"cpu\": 97}"));

        mvc.perform(bearer(get("/api/v1/dashboard/summary"), operatorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeIncidents.total").value(2))
                .andExpect(jsonPath("$.activeIncidents.critical").value(1))
                .andExpect(jsonPath("$.activeIncidents.high").value(1))
                .andExpect(jsonPath("$.activeIncidents.unassigned").value(2))
                .andExpect(jsonPath("$.services.degraded").value(1))
                .andExpect(jsonPath("$.devices.down").value(1));
        mvc.perform(bearer(get("/api/v1/dashboard/incident-trend").param("hours", "24")
                        .param("tz", "Asia/Kolkata"), operatorToken()))
                .andExpect(jsonPath("$.length()").value(24));
    }
}
