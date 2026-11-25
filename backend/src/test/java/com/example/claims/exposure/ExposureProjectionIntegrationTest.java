package com.example.claims.exposure;

import com.example.claims.TestcontainersConfiguration;
import com.example.claims.common.events.ClaimEvent;
import com.example.claims.common.events.TestEvents;
import com.example.claims.exposure.ExposureViewRepository.ExposureLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.example.claims.common.events.ClaimEventType.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exposure totals must equal the latest reserve of each open claim, however often events are
 * redelivered. Each test uses its own made-up currency code so totals are isolated from other tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ExposureProjectionIntegrationTest {

    static final Instant T = Instant.parse("2026-11-20T10:00:00Z");

    @Autowired
    ExposureProjection projection;
    @Autowired
    ExposureViewRepository views;

    final UUID claimant = UUID.randomUUID();

    @Test
    void reserveChangesReplaceRatherThanAdd() {
        TestEvents c = TestEvents.claim(claimant, "XAA", T);
        projection.apply(c.next(CLAIM_SUBMITTED, T));
        ClaimEvent firstEstimate = c.reserve("1000.00").next(RESERVE_CHANGED, T);
        projection.apply(firstEstimate);
        projection.apply(c.reserve("1500.00").next(RESERVE_CHANGED, T));

        assertThat(projection.apply(firstEstimate)).isFalse(); // redelivery of an older event
        assertThat(total("XAA")).hasValueSatisfying(l -> {
            assertThat(l.outstandingReserve()).isEqualByComparingTo("1500.00");
            assertThat(l.openClaims()).isEqualTo(1);
        });
    }

    @Test
    void duplicateDeliveryOfTheSameEventCannotDoubleCount() {
        TestEvents c = TestEvents.claim(claimant, "XAB", T);
        ClaimEvent reserved = c.reserve("700.00").next(RESERVE_CHANGED, T);
        for (int i = 0; i < 5; i++) {
            projection.apply(reserved);
        }
        assertThat(total("XAB")).hasValueSatisfying(l ->
                assertThat(l.outstandingReserve()).isEqualByComparingTo("700.00"));
    }

    @Test
    void closedClaimLeavesExposureAndAStaleEventCannotBringItBack() {
        TestEvents c = TestEvents.claim(claimant, "XAC", T);
        projection.apply(c.next(CLAIM_SUBMITTED, T));
        ClaimEvent reserved = c.reserve("2000.00").next(RESERVE_CHANGED, T);
        projection.apply(reserved);
        projection.apply(c.status("SETTLED").reserve("0").closedAt(T).next(CLAIM_SETTLED, T));

        assertThat(total("XAC")).isEmpty();
        assertThat(projection.apply(reserved)).isFalse();
        assertThat(total("XAC")).isEmpty();
    }

    @Test
    void sumsOpenClaimsAndCountsThoseWithoutAReserve() {
        projection.apply(TestEvents.claim(claimant, "XAD", T).reserve("100.25").next(RESERVE_CHANGED, T));
        projection.apply(TestEvents.claim(claimant, "XAD", T).reserve("200.50").next(RESERVE_CHANGED, T));
        projection.apply(TestEvents.claim(claimant, "XAD", T).next(CLAIM_SUBMITTED, T)); // no reserve yet

        assertThat(total("XAD")).hasValueSatisfying(l -> {
            assertThat(l.openClaims()).isEqualTo(3);
            assertThat(l.unreservedClaims()).isEqualTo(1);
            assertThat(l.outstandingReserve()).isEqualByComparingTo(new BigDecimal("300.75"));
        });
    }

    private Optional<ExposureLine> total(String currency) {
        return views.openExposure().stream().filter(l -> l.currency().equals(currency)).findFirst();
    }
}
