package com.example.claims.timeline;

import com.example.claims.TestcontainersConfiguration;
import com.example.claims.common.events.ClaimEvent;
import com.example.claims.common.events.TestEvents;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.example.claims.common.events.ClaimEventType.*;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TimelineProjectionIntegrationTest {

    static final Instant T = Instant.parse("2026-11-20T10:00:00Z");

    @Autowired
    TimelineProjection projection;
    @Autowired
    TimelineRepository timeline;

    final UUID claimant = UUID.randomUUID();

    @Test
    void redeliveredEventIsStoredOnce() {
        ClaimEvent e = TestEvents.claim(claimant, "SGD", T).next(CLAIM_SUBMITTED, T,
                "claimedAmount", new BigDecimal("100"));
        assertThat(projection.apply(e)).isTrue();
        assertThat(projection.apply(e)).isFalse();
        assertThat(timeline.forStaff(e.claimId())).hasSize(1);
    }

    @Test
    void claimantSeesOnlyVisibleEntriesOfOwnClaimStaffSeeAllInOrder() {
        TestEvents c = TestEvents.claim(claimant, "SGD", T);
        projection.apply(c.next(CLAIM_SUBMITTED, T, "claimedAmount", new BigDecimal("100")));
        projection.apply(c.reserve("80").next(RESERVE_CHANGED, T.plusSeconds(1),
                "previousAmount", BigDecimal.ZERO, "newAmount", new BigDecimal("80"), "reason", "estimate"));
        projection.apply(c.status("ASSIGNED").officer(UUID.randomUUID()).next(CLAIM_ASSIGNED, T.plusSeconds(2)));

        assertThat(timeline.forStaff(c.claimId())).extracting(TimelineRepository.Entry::eventType)
                .containsExactly("ClaimSubmitted", "ReserveChanged", "ClaimAssigned");
        assertThat(timeline.forClaimant(c.claimId(), claimant)).extracting(TimelineRepository.Entry::eventType)
                .containsExactly("ClaimSubmitted", "ClaimAssigned");
        assertThat(timeline.forClaimant(c.claimId(), UUID.randomUUID())).isEmpty();
    }
}
