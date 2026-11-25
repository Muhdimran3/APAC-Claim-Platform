package com.example.claims.claim;

import com.example.claims.common.web.ApiExceptions.ForbiddenException;
import com.example.claims.common.web.ApiExceptions.NotFoundException;
import org.junit.jupiter.api.Test;

import static com.example.claims.claim.ClaimTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimAccessTest {

    @Test
    void claimantSeesOwnClaimButAnotherClaimantGetsNotFound() {
        Claim alices = underAssessment();
        assertThatCode(() -> ClaimAccess.requireView(alices, ALICE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ClaimAccess.requireView(alices, BEN)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void allStaffCanViewAnyClaim() {
        Claim c = underAssessment();
        assertThatCode(() -> ClaimAccess.requireView(c, OSCAR)).doesNotThrowAnyException();
        assertThatCode(() -> ClaimAccess.requireView(c, MAYA)).doesNotThrowAnyException();
    }

    @Test
    void onlyAssignedOfficerOrManagerCanActAsStaff() {
        Claim c = underAssessment(); // assigned to OLIVIA
        assertThatCode(() -> ClaimAccess.requireStaffAction(c, OLIVIA)).doesNotThrowAnyException();
        assertThatCode(() -> ClaimAccess.requireStaffAction(c, MAYA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ClaimAccess.requireStaffAction(c, OSCAR))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not assigned to you");
    }

    @Test
    void claimantCannotActAsStaffEvenOnOwnClaim() {
        Claim c = underAssessment();
        assertThatThrownBy(() -> ClaimAccess.requireStaffAction(c, ALICE)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void otherClaimantsAreToldNotFoundRatherThanForbidden() {
        Claim c = underAssessment();
        assertThatThrownBy(() -> ClaimAccess.requireStaffAction(c, BEN)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> ClaimAccess.requireOwner(c, BEN)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void onlyOwningClaimantCanRespondToInformationRequests() {
        Claim c = underAssessment();
        assertThatCode(() -> ClaimAccess.requireOwner(c, ALICE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ClaimAccess.requireOwner(c, OLIVIA)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void availableActionsApplyAssignment() {
        Claim c = underAssessment();
        assertThat(ClaimAccess.availableActions(c, OLIVIA))
                .containsExactlyInAnyOrder(ClaimAction.REQUEST_INFO, ClaimAction.APPROVE, ClaimAction.REJECT);
        assertThat(ClaimAccess.availableActions(c, MAYA))
                .containsExactlyInAnyOrder(ClaimAction.REQUEST_INFO, ClaimAction.APPROVE, ClaimAction.REJECT);
        assertThat(ClaimAccess.availableActions(c, OSCAR)).isEmpty();
        assertThat(ClaimAccess.availableActions(c, ALICE)).isEmpty();
    }

    @Test
    void anyOfficerCanPickUpAnUnassignedClaim() {
        Claim c = submittedBy(ALICE);
        assertThat(ClaimAccess.availableActions(c, OSCAR)).containsExactly(ClaimAction.ASSIGN);
        assertThat(ClaimAccess.availableActions(c, OLIVIA)).containsExactly(ClaimAction.ASSIGN);
    }
}
