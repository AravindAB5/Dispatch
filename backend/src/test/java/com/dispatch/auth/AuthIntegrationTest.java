package com.dispatch.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dispatch.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.dispatch.support.TestData;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthIntegrationTest extends IntegrationTest {

    private static final String RULE = """
            {"name":"Gateway offline","conditionType":"EVENT_TYPE","eventType":"GATEWAY_OFFLINE",
             "incidentSeverity":"HIGH"}
            """;

    @Test
    void loginReturnsABearerTokenAndTheUserProfile() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ADMIN@test.local\",\"password\":\"%s\"}".formatted(TestData.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void wrongPasswordsAndUnknownEmailsGetTheSameGenericError() throws Exception {
        for (String email : new String[] {TestData.ADMIN_EMAIL, "nobody@test.local"}) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }
    }

    @Test
    void deactivatedAccountsCannotSignIn() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(TestData.INACTIVE_EMAIL, TestData.PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void loginRequestsAreValidated() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tamperedTokensAreRejected() throws Exception {
        String token = adminToken();
        mvc.perform(bearer(get("/api/v1/incidents"), token.substring(0, token.length() - 4) + "abcd"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void meReturnsTheSignedInUser() throws Exception {
        mvc.perform(bearer(get("/api/v1/auth/me"), operatorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TestData.OPERATOR_EMAIL))
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    void onlyAdministratorsCanManageAlertRules() throws Exception {
        mvc.perform(bearer(post("/api/v1/rules"), operatorToken())
                        .contentType(MediaType.APPLICATION_JSON).content(RULE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(bearer(post("/api/v1/rules"), adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(RULE))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.condition").value("GATEWAY_OFFLINE received"));
    }

    @Test
    void operatorsCanReadRulesButNotIngestEvents() throws Exception {
        String token = operatorToken();
        mvc.perform(bearer(get("/api/v1/rules"), token)).andExpect(status().isOk());
        mvc.perform(bearer(post("/api/v1/events"), token).contentType(MediaType.APPLICATION_JSON)
                        .content(deviceEvent("DEVICE_OFFLINE", "CRITICAL", "DEVICE-108")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theIngestionKeyCanOnlySubmitEvents() throws Exception {
        mvc.perform(get("/api/v1/incidents").header("X-API-Key", TestData.INGEST_KEY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/incidents").header("X-API-Key", "not-the-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("API_KEY_INVALID"));
    }

    @Test
    void deactivatingAnAccountRevokesItsExistingTokenImmediately() throws Exception {
        String operator = operatorToken();
        mvc.perform(bearer(get("/api/v1/incidents"), operator)).andExpect(status().isOk());

        mvc.perform(bearer(patch("/api/v1/users/{id}", data.operator.getId()), adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());

        mvc.perform(bearer(get("/api/v1/incidents"), operator))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void roleChangesApplyWithoutWaitingForTheTokenToExpire() throws Exception {
        String admin = adminToken();
        String created = mvc.perform(bearer(post("/api/v1/users"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Second Admin","email":"second@test.local","password":"%s","role":"ADMIN"}
                                """.formatted(TestData.PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number secondId = JsonPath.read(created, "$.id");
        String second = login("second@test.local", TestData.PASSWORD);
        mvc.perform(bearer(post("/api/v1/rules"), second).contentType(MediaType.APPLICATION_JSON).content(RULE))
                .andExpect(status().isCreated());

        mvc.perform(bearer(patch("/api/v1/users/{id}", secondId), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OPERATOR\"}"))
                .andExpect(status().isOk());

        mvc.perform(bearer(post("/api/v1/rules"), second).contentType(MediaType.APPLICATION_JSON)
                        .content(RULE.replace("Gateway offline", "Another rule")))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordsBeyondBcryptsByteLimitAreRejectedRatherThanCrashing() throws Exception {
        String cyrillic = "п".repeat(40); // 40 characters but 80 bytes in UTF-8
        mvc.perform(bearer(post("/api/v1/users"), adminToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Long Password","email":"long@test.local","password":"%s","role":"OPERATOR"}
                                """.formatted(cyrillic)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(TestData.ADMIN_EMAIL, cyrillic)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void ruleDefinitionsAreValidatedAcrossFields() throws Exception {
        mvc.perform(bearer(post("/api/v1/rules"), adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CPU","conditionType":"METRIC_THRESHOLD","eventType":"CPU_USAGE_HIGH",
                                 "incidentSeverity":"HIGH","titleTemplate":"{bogus}"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'metricKey')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'operator')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'threshold')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'titleTemplate')]").exists());
    }
}
