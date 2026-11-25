package com.example.claims.claim;

import com.example.claims.common.web.ApiExceptions.ConflictException;
import com.example.claims.user.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.example.claims.claim.ClaimTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aggregate behaviour on top of the lifecycle: reserve, closing and reassignment. */
class ClaimTest {

    private static final BigDecimal AMOUNT = new BigDecimal("2000.00");

    @Test
    void newClaimIsSubmittedWithZeroReserveAndPolicyCurrency() {
        Claim c = submittedBy(ALICE);
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(c.getReserveAmount()).isZero();
        assertThat(c.getCurrency()).isEqualTo("SGD");
        assertThat(c.getAssignedOfficerId()).isNull();
        assertThat(c.getClosedAt()).isNull();
    }

    @Test
    void approvalAlignsReserveToApprovedAmount() {
        Claim c = underAssessment();
        c.changeReserve(new BigDecimal("3000.00"), NOW);
        c.approve(AMOUNT, Role.OFFICER, NOW);
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(c.getReserveAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(c.getClosedAt()).isNull();
    }

    @Test
    void rejectionReleasesReserveAndClosesClaim() {
        Claim c = underAssessment();
        c.changeReserve(AMOUNT, NOW);
        c.reject(Role.OFFICER, NOW);
        assertThat(c.getReserveAmount()).isZero();
        assertThat(c.getClosedAt()).isEqualTo(NOW);
    }

    @Test
    void settlementRecordsPaymentReleasesReserveAndClosesClaim() {
        Claim c = underAssessment();
        c.approve(AMOUNT, Role.OFFICER, NOW);
        c.settle(AMOUNT, Role.OFFICER, NOW);
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(c.getSettledAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(c.getReserveAmount()).isZero();
        assertThat(c.getClosedAt()).isEqualTo(NOW);
    }

    @Test
    void reserveCannotChangeOnClosedClaim() {
        Claim c = underAssessment();
        c.reject(Role.OFFICER, NOW);
        assertThatThrownBy(() -> c.changeReserve(AMOUNT, NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void unchangedReserveIsRejected() {
        Claim c = underAssessment();
        assertThatThrownBy(() -> c.changeReserve(BigDecimal.ZERO, NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void reassignmentKeepsStatusButNotOnClosedClaimsOrToSameOfficer() {
        Claim c = underAssessment();
        c.reassignTo(OSCAR.id(), NOW);
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);
        assertThat(c.isAssignedTo(OSCAR.id())).isTrue();

        assertThatThrownBy(() -> c.reassignTo(OSCAR.id(), NOW)).isInstanceOf(ConflictException.class);
        c.reject(Role.MANAGER, NOW);
        assertThatThrownBy(() -> c.reassignTo(OLIVIA.id(), NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void statusChangesStillGoThroughLifecycle() {
        Claim c = submittedBy(ALICE);
        assertThatThrownBy(() -> c.approve(AMOUNT, Role.OFFICER, NOW))
                .isInstanceOf(InvalidClaimTransitionException.class);
        assertThatThrownBy(() -> c.assignTo(ALICE.id(), Role.CLAIMANT, NOW))
                .isInstanceOf(ClaimActionNotPermittedException.class);
    }
}
