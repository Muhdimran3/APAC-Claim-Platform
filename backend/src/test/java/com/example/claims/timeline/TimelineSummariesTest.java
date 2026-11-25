package com.example.claims.timeline;

import com.example.claims.common.events.ClaimEventType;
import com.example.claims.common.events.TestEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineSummariesTest {

    private static final Instant T = Instant.parse("2026-11-20T10:00:00Z");

    private final TestEvents claim = TestEvents.claim(UUID.randomUUID(), "SGD", T);

    @ParameterizedTest
    @EnumSource(value = ClaimEventType.class, names = "RESERVE_CHANGED", mode = EnumSource.Mode.EXCLUDE)
    void everythingExceptReserveChangesIsVisibleToTheClaimant(ClaimEventType type) {
        assertThat(TimelineSummaries.describe(claim.next(type, T)).visibleToClaimant()).isTrue();
    }

    @Test
    void reserveChangesAreStaffOnly() {
        var line = TimelineSummaries.describe(claim.next(ClaimEventType.RESERVE_CHANGED, T,
                "previousAmount", new BigDecimal("0"), "newAmount", new BigDecimal("3000"), "reason", "quote"));
        assertThat(line.visibleToClaimant()).isFalse();
        assertThat(line.summary()).isEqualTo("Reserve changed from SGD 0.00 to SGD 3,000.00 (quote)");
    }

    @Test
    void decisionsCarryAmountOrReason() {
        assertThat(TimelineSummaries.describe(claim.next(ClaimEventType.CLAIM_APPROVED, T,
                "approvedAmount", new BigDecimal("2400.5"))).summary())
                .isEqualTo("Claim approved for SGD 2,400.50");
        assertThat(TimelineSummaries.describe(claim.next(ClaimEventType.CLAIM_REJECTED, T,
                "reason", "Not covered")).summary())
                .isEqualTo("Claim rejected: Not covered");
    }

    @Test
    void longQuestionsAreTruncatedToColumnSize() {
        var line = TimelineSummaries.describe(claim.next(ClaimEventType.INFORMATION_REQUESTED, T,
                "question", "x".repeat(2000)));
        assertThat(line.summary()).hasSize(TimelineSummaries.MAX_SUMMARY).endsWith("…");
    }
}
