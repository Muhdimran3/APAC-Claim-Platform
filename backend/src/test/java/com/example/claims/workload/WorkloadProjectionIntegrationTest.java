package com.example.claims.workload;

import com.example.claims.TestcontainersConfiguration;
import com.example.claims.common.events.ClaimEvent;
import com.example.claims.common.events.TestEvents;
import com.example.claims.workload.WorkloadViewRepository.OfficerStats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.claims.common.events.ClaimEventType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Idempotency and the workload figures, using synthetic events (random claim and officer ids). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WorkloadProjectionIntegrationTest {

    static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");

    @Autowired
    WorkloadProjection projection;
    @Autowired
    WorkloadViewRepository views;
    @Autowired
    JdbcTemplate jdbc;

    final UUID claimant = UUID.randomUUID();
    final UUID officer = UUID.randomUUID();

    @Test
    void duplicateEventIsIgnored() {
        ClaimEvent submitted = TestEvents.claim(claimant, "SGD", T0).next(CLAIM_SUBMITTED, T0);
        assertThat(projection.apply(submitted)).isTrue();
        assertThat(projection.apply(submitted)).isFalse();
        assertThat(row(submitted.claimId())).containsEntry("last_claim_version", 0L);
    }

    @Test
    void staleEventArrivingAfterANewerOneIsIgnored() {
        TestEvents c = TestEvents.claim(claimant, "SGD", T0);
        ClaimEvent v0 = c.next(CLAIM_SUBMITTED, T0);
        ClaimEvent v1 = c.status("ASSIGNED").officer(officer).next(CLAIM_ASSIGNED, T0.plusSeconds(60));

        assertThat(projection.apply(v1)).isTrue();
        assertThat(projection.apply(v0)).isFalse();

        Map<String, Object> row = row(c.claimId());
        assertThat(row).containsEntry("status", "ASSIGNED").containsEntry("officer_id", officer)
                .containsEntry("last_claim_version", 1L);
    }

    @Test
    void replayingTheWholeHistoryChangesNothing() {
        TestEvents c = TestEvents.claim(claimant, "SGD", T0);
        List<ClaimEvent> history = List.of(
                c.next(CLAIM_SUBMITTED, T0),
                c.status("ASSIGNED").officer(officer).next(CLAIM_ASSIGNED, T0.plusSeconds(1)),
                c.status("UNDER_ASSESSMENT").next(CLAIM_ASSESSMENT_STARTED, T0.plusSeconds(2)),
                c.status("REJECTED").closedAt(T0.plusSeconds(3)).next(CLAIM_REJECTED, T0.plusSeconds(3)));
        history.forEach(projection::apply);
        Map<String, Object> before = row(c.claimId());

        assertThat(history).allSatisfy(e -> assertThat(projection.apply(e)).isFalse());
        assertThat(row(c.claimId())).isEqualTo(before).containsEntry("status", "REJECTED");
    }

    @Test
    void reassignmentDoesNotResetTimeInStatus() {
        TestEvents c = TestEvents.claim(claimant, "SGD", T0);
        projection.apply(c.next(CLAIM_SUBMITTED, T0));
        projection.apply(c.status("ASSIGNED").officer(officer).next(CLAIM_ASSIGNED, T0.plusSeconds(60)));
        projection.apply(c.officer(UUID.randomUUID()).next(CLAIM_ASSIGNED, T0.plusSeconds(120)));

        assertThat(jdbc.queryForObject("SELECT status_changed_at FROM workload_claim_view WHERE claim_id = ?",
                java.sql.Timestamp.class, c.claimId()).toInstant()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void officerFiguresCountOpenAgedAndRecentlyClosed() {
        Instant now = T0;
        // Open, submitted 20 days ago -> aged
        TestEvents a = TestEvents.claim(claimant, "SGD", now.minus(Duration.ofDays(20)));
        projection.apply(a.status("UNDER_ASSESSMENT").officer(officer).next(CLAIM_ASSESSMENT_STARTED, now));
        // Open, fresh, awaiting the claimant
        TestEvents b = TestEvents.claim(claimant, "SGD", now.minus(Duration.ofDays(1)));
        projection.apply(b.status("AWAITING_INFO").officer(officer).next(INFORMATION_REQUESTED, now));
        // Closed 2 days ago after 4 days open
        TestEvents c = TestEvents.claim(claimant, "SGD", now.minus(Duration.ofDays(6)));
        projection.apply(c.status("SETTLED").officer(officer).closedAt(now.minus(Duration.ofDays(2)))
                .next(CLAIM_SETTLED, now.minus(Duration.ofDays(2))));

        OfficerStats s = views.officerStats(officer, now.minus(Duration.ofDays(14)), now.minus(Duration.ofDays(30)))
                .get(0);
        assertThat(s.open()).isEqualTo(2);
        assertThat(s.openByStatus()).containsEntry("UNDER_ASSESSMENT", 1).containsEntry("AWAITING_INFO", 1)
                .containsEntry("ASSIGNED", 0);
        assertThat(s.aged()).isEqualTo(1);
        assertThat(s.oldestOpenSubmittedAt()).isEqualTo(now.minus(Duration.ofDays(20)));
        assertThat(s.closedRecently()).isEqualTo(1);
        assertThat(s.avgDaysToClose()).isCloseTo(4.0, within(0.001));
    }

    private Map<String, Object> row(UUID claimId) {
        return jdbc.queryForMap("SELECT * FROM workload_claim_view WHERE claim_id = ?", claimId);
    }
}
