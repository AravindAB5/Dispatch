package com.dispatch.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base for API-level tests: the full application (security, MVC, JPA, WebSocket) against a real
 * PostgreSQL started by Testcontainers. All subclasses share one Spring context and one container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestData.class})
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestData data;

    @BeforeEach
    void resetDatabase() {
        data.reset();
    }

    protected String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    protected String adminToken() throws Exception {
        return login(TestData.ADMIN_EMAIL, TestData.PASSWORD);
    }

    protected String operatorToken() throws Exception {
        return login(TestData.OPERATOR_EMAIL, TestData.PASSWORD);
    }

    protected static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    protected ResultActions ingest(String json) throws Exception {
        return mvc.perform(post("/api/v1/events")
                .header("X-API-Key", TestData.INGEST_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    protected static String deviceEvent(String type, String severity, String deviceId, String metadataJson) {
        return """
                {"eventType":"%s","severity":"%s","sourceType":"DEVICE","sourceId":"%s",
                 "message":"%s from %s","metadata":%s}
                """.formatted(type, severity, deviceId, type, deviceId, metadataJson);
    }

    protected static String deviceEvent(String type, String severity, String deviceId) {
        return deviceEvent(type, severity, deviceId, "{}");
    }

    protected static long incidentIdFrom(String ingestionResponse) {
        return ((Number) JsonPath.read(ingestionResponse, "$.incidentActions[0].incidentId")).longValue();
    }
}
