package com.example.claims;

import com.example.claims.outbox.OutboxPoller;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;

import static com.example.claims.SeedData.*;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full asynchronous path: REST command → outbox → poller → Kafka → three consumer groups →
 * projection tables → read endpoints. Uses Alice's AUD policy, which no other test touches, so
 * the AUD exposure figures are exact.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReadModelsEndToEndTest {

    static final String ALICE_AU_POLICY = "00000000-0000-0000-0000-000000001002";

    @Autowired
    WebApplicationContext context;
    @Autowired
    OutboxPoller poller;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void commandsFlowThroughKafkaIntoTimelineWorkloadAndExposure() throws Exception {
        String id = JsonPath.read(call(post("/api/claims"), "alice", """
                {"policyId": "%s", "incidentDate": "2026-10-15", "description": "Storm damage to roof",
                 "claimedAmount": 5000.00}""".formatted(ALICE_AU_POLICY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        call(post("/api/claims/{id}/assign", id), "olivia", "{}").andExpect(status().isOk());
        call(post("/api/claims/{id}/start-assessment", id), "olivia").andExpect(status().isOk());
        call(put("/api/claims/{id}/reserve", id), "olivia", """
                {"amount": 4000.00, "reason": "Builder's quote"}""").andExpect(status().isOk());

        while (poller.publishBatch() > 0) {
            // publish everything pending (other tests may have left rows too)
        }

        // Timeline: staff see all 4 events; the claimant doesn't see the reserve change.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                call(get("/api/claims/{id}/timeline", id), "olivia").andExpect(jsonPath("$", hasSize(4))));
        call(get("/api/claims/{id}/timeline", id), "olivia")
                .andExpect(jsonPath("$[*].eventType", contains(
                        "ClaimSubmitted", "ClaimAssigned", "ClaimAssessmentStarted", "ReserveChanged")))
                .andExpect(jsonPath("$[1].actor").value("Olivia Lim"));
        call(get("/api/claims/{id}/timeline", id), "alice")
                .andExpect(jsonPath("$[*].eventType", contains(
                        "ClaimSubmitted", "ClaimAssigned", "ClaimAssessmentStarted")))
                .andExpect(jsonPath("$[0].summary", containsString("AUD 5,000.00")));
        call(get("/api/claims/{id}/timeline", id), "ben").andExpect(jsonPath("$", empty()));

        // Exposure: exactly this claim's reserve in AUD (no other test uses AUD).
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                call(get("/api/dashboard/exposure"), "maya")
                        .andExpect(jsonPath("$.byCurrency[?(@.currency == 'AUD')].outstandingReserve", contains(4000.0)))
                        .andExpect(jsonPath("$.byCurrency[?(@.currency == 'AUD')].openClaims", contains(1))));
        call(get("/api/dashboard/exposure"), "maya")
                .andExpect(jsonPath("$.breakdown[?(@.currency == 'AUD')].market", contains("AU")))
                .andExpect(jsonPath("$.breakdown[?(@.currency == 'AUD')].product", contains("PROPERTY")));

        // Workload: an officer sees only their own row; a manager sees every officer.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                call(get("/api/dashboard/workload"), "olivia")
                        .andExpect(jsonPath("$.officers[0].openByStatus.UNDER_ASSESSMENT", not(is(0)))));
        call(get("/api/dashboard/workload"), "olivia")
                .andExpect(jsonPath("$.officers", hasSize(1)))
                .andExpect(jsonPath("$.officers[0].officerId").value(OLIVIA))
                .andExpect(jsonPath("$.unassigned.open").exists());
        call(get("/api/dashboard/workload"), "maya")
                .andExpect(jsonPath("$.officers[*].officerId", hasItems(OLIVIA, OSCAR)))
                .andExpect(jsonPath("$.unassigned.open").exists());
    }

    @Test
    void dashboardsEnforceRoles() throws Exception {
        call(get("/api/dashboard/workload"), "alice").andExpect(status().isForbidden());
        call(get("/api/dashboard/exposure"), "alice").andExpect(status().isForbidden());
        call(get("/api/dashboard/exposure"), "olivia").andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/workload")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims/{id}/timeline", ALICE)).andExpect(status().isUnauthorized());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String user) throws Exception {
        return mvc.perform(request.header("X-User", user));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String user, String json) throws Exception {
        return mvc.perform(request.header("X-User", user).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
