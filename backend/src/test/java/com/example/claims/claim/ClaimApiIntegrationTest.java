package com.example.claims.claim;

import com.example.claims.TestcontainersConfiguration;
import com.example.claims.outbox.OutboxEvent;
import com.example.claims.outbox.OutboxRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

import java.util.List;
import java.util.UUID;

import static com.example.claims.SeedData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through the HTTP layer against real Postgres: controllers, X-User resolution,
 * services, access rules, error mapping and outbox rows. Each test creates its own claims.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ClaimApiIntegrationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    OutboxRepository outbox;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void fullLifecycleFromSubmissionToSettlement() throws Exception {
        String id = submit("alice", "2500.00");

        call(post("/api/claims/{id}/assign", id), "olivia")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedOfficer.displayName").value("Olivia Lim"));
        call(post("/api/claims/{id}/start-assessment", id), "olivia")
                .andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"));

        String requestId = JsonPath.read(call(post("/api/claims/{id}/info-requests", id), "olivia",
                        """
                        {"question": "Please upload the police report"}""")
                .andExpect(jsonPath("$.status").value("AWAITING_INFO"))
                .andExpect(jsonPath("$.infoRequests[0].open").value(true))
                .andReturn().getResponse().getContentAsString(), "$.infoRequests[0].id");

        call(get("/api/claims/{id}", id), "alice")
                .andExpect(jsonPath("$.availableActions", contains("PROVIDE_INFO")));
        call(post("/api/claims/{id}/info-requests/{rid}/response", id, requestId), "alice",
                """
                {"response": "Report no. 12345 attached"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"))
                .andExpect(jsonPath("$.infoRequests[0].open").value(false));

        call(put("/api/claims/{id}/reserve", id), "olivia", """
                {"amount": 3000.00, "reason": "Initial estimate from repair quote"}""")
                .andExpect(jsonPath("$.reserveAmount").value(3000.00));
        call(post("/api/claims/{id}/decision", id), "olivia", """
                {"outcome": "APPROVED", "amount": 2400.00, "reason": "Covered; excess applied"}""")
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reserveAmount").value(2400.00))
                .andExpect(jsonPath("$.decision.approvedAmount").value(2400.00));
        call(post("/api/claims/{id}/settlement", id), "olivia", """
                {"amount": 2400.00}""")
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.reserveAmount").value(0))
                .andExpect(jsonPath("$.settledAmount").value(2400.00))
                .andExpect(jsonPath("$.closedAt").exists())
                .andExpect(jsonPath("$.availableActions", empty())) // present and empty, not just absent
                .andExpect(jsonPath("$.reserveHistory.length()").value(3)); // set, approve, settle

        // One outbox event per state change, in order, with strictly increasing claim versions.
        List<OutboxEvent> events = outbox.findByAggregateIdOrderBySeq(UUID.fromString(id));
        assertThat(events).extracting(OutboxEvent::getEventType).containsExactly(
                "ClaimSubmitted", "ClaimAssigned", "ClaimAssessmentStarted", "InformationRequested",
                "InformationProvided", "ReserveChanged", "ClaimApproved", "ClaimSettled");
        assertThat(events).extracting(e -> ((Number) JsonPath.read(e.getPayload(), "$.claimVersion")).longValue())
                .containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(events).allSatisfy(e -> assertThat(e.getPublishedAt()).isNull()); // poller hasn't run
        String last = events.get(events.size() - 1).getPayload();
        assertThat((String) JsonPath.read(last, "$.payload.snapshot.status")).isEqualTo("SETTLED");
        assertThat((String) JsonPath.read(last, "$.payload.snapshot.market")).isEqualTo("SG");
    }

    @Test
    void rejectionClosesClaimAndClaimantSeesTheReason() throws Exception {
        String id = assessedBy("olivia");
        call(post("/api/claims/{id}/decision", id), "olivia", """
                {"outcome": "REJECTED", "reason": "Incident not covered by policy"}""")
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.closedAt").exists());
        call(get("/api/claims/{id}", id), "alice")
                .andExpect(jsonPath("$.decision.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.decision.reason").value("Incident not covered by policy"));
    }

    @Nested
    class Authentication {

        @Test
        void missingHeaderIs401() throws Exception {
            mvc.perform(get("/api/claims")).andExpect(status().isUnauthorized());
        }

        @Test
        void unknownUserIs401() throws Exception {
            call(get("/api/claims"), "mallory").andExpect(status().isUnauthorized());
        }

        @Test
        void roleComesFromTheDatabase() throws Exception {
            call(get("/api/me"), "olivia")
                    .andExpect(jsonPath("$.role").value("OFFICER"))
                    .andExpect(jsonPath("$.id").value(OLIVIA));
        }
    }

    @Nested
    class Ownership {

        @Test
        void anotherClaimantGets404AndDoesNotSeeTheClaimInTheirList() throws Exception {
            String id = submit("alice", "1000.00");
            call(get("/api/claims/{id}", id), "ben").andExpect(status().isNotFound());
            call(get("/api/claims"), "ben").andExpect(jsonPath("$[*].id", not(hasItem(id))));
            call(get("/api/claims"), "alice").andExpect(jsonPath("$[*].id", hasItem(id)));
        }

        @Test
        void claimantCannotSubmitOnSomeoneElsesPolicy() throws Exception {
            call(post("/api/claims"), "alice", claimBody(BEN_HK_POLICY, "2026-11-01", "1000.00"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void claimantViewHidesReserveAndInternalNotes() throws Exception {
            String id = assessedBy("olivia");
            call(put("/api/claims/{id}/reserve", id), "olivia", """
                    {"amount": 900.00, "reason": "estimate"}""");
            call(post("/api/claims/{id}/notes", id), "olivia", """
                    {"body": "Possible fraud indicators, check dashcam"}""")
                    .andExpect(jsonPath("$.notes[0].body").value(containsString("dashcam")));

            call(get("/api/claims/{id}", id), "alice")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reserveAmount").doesNotExist())
                    .andExpect(jsonPath("$.reserveHistory").doesNotExist())
                    .andExpect(jsonPath("$.notes").doesNotExist());
        }
    }

    @Nested
    class Assignment {

        @Test
        void officerCannotActOnClaimAssignedToSomeoneElseButManagerCan() throws Exception {
            String id = submit("alice", "1000.00");
            call(post("/api/claims/{id}/assign", id), "olivia");
            call(post("/api/claims/{id}/start-assessment", id), "oscar")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value(containsString("not assigned to you")));
            call(post("/api/claims/{id}/start-assessment", id), "maya")
                    .andExpect(status().isOk());
        }

        @Test
        void officerCanOnlyAssignToThemselves() throws Exception {
            String id = submit("alice", "1000.00");
            call(post("/api/claims/{id}/assign", id), "olivia", "{\"officerId\": \"" + OSCAR + "\"}")
                    .andExpect(status().isForbidden());
        }

        @Test
        void managerMustNameAnOfficer() throws Exception {
            String id = submit("alice", "1000.00");
            call(post("/api/claims/{id}/assign", id), "maya").andExpect(status().isBadRequest());
            call(post("/api/claims/{id}/assign", id), "maya", "{\"officerId\": \"" + ALICE + "\"}")
                    .andExpect(status().isUnprocessableContent());
            call(post("/api/claims/{id}/assign", id), "maya", "{\"officerId\": \"" + OSCAR + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assignedOfficer.id").value(OSCAR));
        }

        @Test
        void managerReassignsWithoutChangingStatusAndOldOfficerLosesAccess() throws Exception {
            String id = assessedBy("olivia");
            call(post("/api/claims/{id}/assign", id), "maya", "{\"officerId\": \"" + OSCAR + "\"}")
                    .andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"))
                    .andExpect(jsonPath("$.assignedOfficer.id").value(OSCAR));
            call(post("/api/claims/{id}/notes", id), "olivia", "{\"body\": \"x\"}")
                    .andExpect(status().isForbidden());
        }

        @Test
        void officerCannotPickUpAnAlreadyAssignedClaim() throws Exception {
            String id = submit("alice", "1000.00");
            call(post("/api/claims/{id}/assign", id), "olivia");
            call(post("/api/claims/{id}/assign", id), "oscar").andExpect(status().isConflict());
        }

        @Test
        void unassignedQueueShowsNewClaims() throws Exception {
            String id = submit("alice", "1000.00");
            call(get("/api/claims?assignee=unassigned"), "oscar").andExpect(jsonPath("$[*].id", hasItem(id)));
            call(get("/api/claims?assignee=me"), "oscar").andExpect(jsonPath("$[*].id", not(hasItem(id))));
            call(get("/api/claims?assignee=whoever"), "oscar").andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void claimantCannotPerformStaffActionsOnOwnClaim() throws Exception {
            String id = submit("alice", "1000.00");
            call(post("/api/claims/{id}/assign", id), "alice").andExpect(status().isForbidden());
            call(post("/api/claims/{id}/start-assessment", id), "alice").andExpect(status().isForbidden());
        }

        @Test
        void actionFromWrongStateIs409() throws Exception {
            String id = submit("alice", "1000.00");
            call(post("/api/claims/{id}/assign", id), "olivia");
            call(post("/api/claims/{id}/decision", id), "olivia", """
                    {"outcome": "APPROVED", "amount": 100.00, "reason": "ok"}""")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("ASSIGNED")));
            call(post("/api/claims/{id}/settlement", id), "olivia", "{\"amount\": 100.00}")
                    .andExpect(status().isConflict());
        }

        @Test
        void cannotDecideWhileAwaitingInformation() throws Exception {
            String id = assessedBy("olivia");
            call(post("/api/claims/{id}/info-requests", id), "olivia", "{\"question\": \"Photos?\"}");
            call(post("/api/claims/{id}/info-requests", id), "olivia", "{\"question\": \"Again?\"}")
                    .andExpect(status().isConflict());
            call(post("/api/claims/{id}/decision", id), "olivia", """
                    {"outcome": "REJECTED", "reason": "no"}""")
                    .andExpect(status().isConflict());
        }

        @Test
        void failedCommandWritesNoEvent() throws Exception {
            String id = submit("alice", "1000.00");
            call(post("/api/claims/{id}/start-assessment", id), "maya").andExpect(status().isConflict());
            assertThat(outbox.findByAggregateIdOrderBySeq(UUID.fromString(id)))
                    .extracting(OutboxEvent::getEventType).containsExactly("ClaimSubmitted");
        }
    }

    @Nested
    class Validation {

        @Test
        void missingFieldsAre400WithFieldErrors() throws Exception {
            call(post("/api/claims"), "alice", "{\"description\": \"\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.policyId").exists())
                    .andExpect(jsonPath("$.errors.description").exists())
                    .andExpect(jsonPath("$.errors.claimedAmount").exists());
        }

        @Test
        void futureIncidentDateAndNegativeAmountAre400() throws Exception {
            call(post("/api/claims"), "alice", claimBody(ALICE_SG_POLICY, "2099-01-01", "-5"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.incidentDate").exists())
                    .andExpect(jsonPath("$.errors.claimedAmount").exists());
        }

        @Test
        void approvalWithoutAmountIs400() throws Exception {
            String id = assessedBy("olivia");
            call(post("/api/claims/{id}/decision", id), "olivia", """
                    {"outcome": "APPROVED", "reason": "ok"}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.amountValidForOutcome").exists());
        }

        @Test
        void malformedJsonIs400() throws Exception {
            call(post("/api/claims"), "alice", "{not json").andExpect(status().isBadRequest());
        }
    }

    @Nested
    class BusinessRules {

        @Test
        void claimAboveSumInsuredIs422() throws Exception {
            call(post("/api/claims"), "alice", claimBody(ALICE_SG_POLICY, "2026-11-01", "80000.01"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value(containsString("sum insured")));
        }

        @Test
        void incidentBeforePolicyStartIs422() throws Exception {
            call(post("/api/claims"), "alice", claimBody(ALICE_SG_POLICY, "2025-12-31", "100.00"))
                    .andExpect(status().isUnprocessableContent());
        }

        @Test
        void settlementAboveApprovedAmountIs422AndClaimStaysApproved() throws Exception {
            String id = assessedBy("olivia");
            call(post("/api/claims/{id}/decision", id), "olivia", """
                    {"outcome": "APPROVED", "amount": 500.00, "reason": "ok"}""");
            call(post("/api/claims/{id}/settlement", id), "olivia", "{\"amount\": 500.01}")
                    .andExpect(status().isUnprocessableContent());
            call(get("/api/claims/{id}", id), "olivia").andExpect(jsonPath("$.status").value("APPROVED"));
        }
    }

    // --- helpers -------------------------------------------------------------------------

    private ResultActions call(MockHttpServletRequestBuilder request, String user) throws Exception {
        return mvc.perform(request.header("X-User", user));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String user, String json) throws Exception {
        return mvc.perform(request.header("X-User", user).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String submit(String claimant, String amount) throws Exception {
        String body = call(post("/api/claims"), claimant, claimBody(ALICE_SG_POLICY, "2026-11-01", amount))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.claimNumber").value(containsString("CLM-")))
                .andExpect(jsonPath("$.currency").value("SGD"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Alice's claim, picked up by the officer and under assessment. */
    private String assessedBy(String officer) throws Exception {
        String id = submit("alice", "1000.00");
        call(post("/api/claims/{id}/assign", id), officer).andExpect(status().isOk());
        call(post("/api/claims/{id}/start-assessment", id), officer).andExpect(status().isOk());
        return id;
    }

    private static String claimBody(String policyId, String incidentDate, String amount) {
        return """
                {"policyId": "%s", "incidentDate": "%s", "description": "Rear-ended at traffic lights",
                 "claimedAmount": %s}""".formatted(policyId, incidentDate, amount);
    }
}
