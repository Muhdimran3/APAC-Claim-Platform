package com.example.claims;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the Flyway migrations against a real Postgres (Testcontainers) and checks the seed data
 * and the invariants the schema enforces. Each test rolls back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchemaMigrationTest {

    private static final String ALICE = "00000000-0000-0000-0000-000000000101";
    private static final String OLIVIA = "00000000-0000-0000-0000-000000000201";
    private static final String SG_POLICY = "00000000-0000-0000-0000-000000001001";

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void seedsUsersForEveryRole() {
        assertThat(jdbc.queryForList("SELECT role FROM app_user GROUP BY role", String.class))
                .containsExactlyInAnyOrder("CLAIMANT", "OFFICER", "MANAGER");
    }

    @Test
    void seedsOnePolicyPerMarketWithItsOwnCurrency() {
        assertThat(jdbc.queryForList("SELECT market || ':' || currency FROM policy", String.class))
                .containsExactlyInAnyOrder("SG:SGD", "HK:HKD", "MY:MYR", "TH:THB", "ID:IDR", "AU:AUD");
    }

    @Test
    void submittedClaimWithoutOfficerIsAccepted() {
        insertClaim(UUID.randomUUID(), "SUBMITTED", null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM claim", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> insertClaim(UUID.randomUUID(), "CLOSED", OLIVIA))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void claimPastSubmittedMustHaveAnOfficer() {
        assertThatThrownBy(() -> insertClaim(UUID.randomUUID(), "UNDER_ASSESSMENT", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneOpenInformationRequestPerClaim() {
        UUID claimId = UUID.randomUUID();
        insertClaim(claimId, "AWAITING_INFO", OLIVIA);
        insertOpenInfoRequest(claimId);
        assertThatThrownBy(() -> insertOpenInfoRequest(claimId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectionDecisionCannotCarryAnAmount() {
        UUID claimId = UUID.randomUUID();
        insertClaim(claimId, "UNDER_ASSESSMENT", OLIVIA);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO decision (id, claim_id, outcome, approved_amount, reason, decided_by, decided_at)
                VALUES (?, ?, 'REJECTED', 100.00, 'not covered', ?::uuid, now())""",
                UUID.randomUUID(), claimId, OLIVIA))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertClaim(UUID id, String status, String officerId) {
        jdbc.update("""
                INSERT INTO claim (id, claim_number, policy_id, claimant_id, assigned_officer_id, status,
                                   incident_date, description, claimed_amount, currency, submitted_at, updated_at)
                VALUES (?, 'CLM-' || nextval('claim_number_seq'), ?::uuid, ?::uuid, ?::uuid, ?,
                        DATE '2026-11-01', 'Rear-ended at traffic lights', 2500.00, 'SGD', now(), now())""",
                id, SG_POLICY, ALICE, officerId, status);
    }

    private void insertOpenInfoRequest(UUID claimId) {
        jdbc.update("""
                INSERT INTO info_request (id, claim_id, requested_by, question, requested_at)
                VALUES (?, ?, ?::uuid, 'Please upload the police report', now())""",
                UUID.randomUUID(), claimId, OLIVIA);
    }
}
