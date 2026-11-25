package com.example.claims.claim;

import com.example.claims.user.Role;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Stream;

import static com.example.claims.claim.ClaimAction.*;
import static com.example.claims.claim.ClaimStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimLifecycleTest {

    /** Mirrors the lifecycle table in AI_JOURNAL.md (Entry 3, section C). Change both together. */
    private static final Map<ClaimAction, ClaimStatus[]> BLUEPRINT = Map.of(
            ASSIGN, new ClaimStatus[]{SUBMITTED, ASSIGNED},
            START_ASSESSMENT, new ClaimStatus[]{ASSIGNED, UNDER_ASSESSMENT},
            REQUEST_INFO, new ClaimStatus[]{UNDER_ASSESSMENT, AWAITING_INFO},
            PROVIDE_INFO, new ClaimStatus[]{AWAITING_INFO, UNDER_ASSESSMENT},
            APPROVE, new ClaimStatus[]{UNDER_ASSESSMENT, APPROVED},
            REJECT, new ClaimStatus[]{UNDER_ASSESSMENT, REJECTED},
            SETTLE, new ClaimStatus[]{APPROVED, SETTLED});

    private static Role permittedRole(ClaimAction action) {
        return action.allowedRoles().iterator().next();
    }

    @Nested
    class Transitions {

        @Test
        void transitionTableMatchesBlueprint() {
            assertThat(EnumSet.allOf(ClaimAction.class)).isEqualTo(BLUEPRINT.keySet());
            BLUEPRINT.forEach((action, fromTo) -> {
                assertThat(action.from()).as("%s from", action).isEqualTo(fromTo[0]);
                assertThat(action.to()).as("%s to", action).isEqualTo(fromTo[1]);
            });
        }

        @Test
        void happyPathWithInformationLoopEndsSettled() {
            ClaimStatus s = SUBMITTED;
            s = ClaimLifecycle.transition(s, ASSIGN, Role.OFFICER);
            s = ClaimLifecycle.transition(s, START_ASSESSMENT, Role.OFFICER);
            s = ClaimLifecycle.transition(s, REQUEST_INFO, Role.OFFICER);
            assertThat(s).isEqualTo(AWAITING_INFO);
            s = ClaimLifecycle.transition(s, PROVIDE_INFO, Role.CLAIMANT);
            assertThat(s).isEqualTo(UNDER_ASSESSMENT);
            s = ClaimLifecycle.transition(s, APPROVE, Role.OFFICER);
            s = ClaimLifecycle.transition(s, SETTLE, Role.OFFICER);
            assertThat(s).isEqualTo(SETTLED);
        }

        @Test
        void informationCanBeRequestedMoreThanOnce() {
            ClaimStatus s = UNDER_ASSESSMENT;
            for (int i = 0; i < 3; i++) {
                s = ClaimLifecycle.transition(s, REQUEST_INFO, Role.OFFICER);
                s = ClaimLifecycle.transition(s, PROVIDE_INFO, Role.CLAIMANT);
            }
            assertThat(s).isEqualTo(UNDER_ASSESSMENT);
        }

        @Test
        void rejectionIsOnlyReachableFromAssessment() {
            assertThat(EnumSet.allOf(ClaimStatus.class))
                    .filteredOn(st -> ClaimLifecycle.nextStatuses(st).contains(REJECTED))
                    .containsExactly(UNDER_ASSESSMENT);
        }

        @Test
        void claimCannotBeDecidedWhileAwaitingInfo() {
            assertThat(ClaimLifecycle.nextStatuses(AWAITING_INFO)).containsExactly(UNDER_ASSESSMENT);
        }

        static Stream<Arguments> invalidStateActionPairs() {
            return Arrays.stream(ClaimStatus.values())
                    .flatMap(status -> Arrays.stream(ClaimAction.values())
                            .filter(action -> action.from() != status)
                            .map(action -> Arguments.of(status, action)));
        }

        @ParameterizedTest(name = "{1} from {0} is rejected")
        @MethodSource("invalidStateActionPairs")
        void everyActionFromTheWrongStateIsRejected(ClaimStatus current, ClaimAction action) {
            assertThatThrownBy(() -> ClaimLifecycle.transition(current, action, permittedRole(action)))
                    .isInstanceOf(InvalidClaimTransitionException.class)
                    .hasMessageContaining(action.name())
                    .hasMessageContaining(current.name());
        }

        @ParameterizedTest
        @EnumSource(value = ClaimStatus.class, names = {"SETTLED", "REJECTED"})
        void terminalStatesAllowNothing(ClaimStatus terminal) {
            assertThat(terminal.isTerminal()).isTrue();
            assertThat(terminal.isOpen()).isFalse();
            assertThat(ClaimLifecycle.nextStatuses(terminal)).isEmpty();
            for (Role role : Role.values()) {
                assertThat(ClaimLifecycle.availableActions(terminal, role)).isEmpty();
            }
        }

        @Test
        void approvedButUnpaidStillCountsAsOpenExposure() {
            assertThat(APPROVED.isOpen()).isTrue();
        }
    }

    @Nested
    class Permissions {

        @ParameterizedTest
        @EnumSource(value = ClaimAction.class, names = "PROVIDE_INFO", mode = EnumSource.Mode.EXCLUDE)
        void claimantCannotPerformStaffActions(ClaimAction action) {
            assertThatThrownBy(() -> ClaimLifecycle.transition(action.from(), action, Role.CLAIMANT))
                    .isInstanceOf(ClaimActionNotPermittedException.class);
        }

        @ParameterizedTest
        @EnumSource(value = Role.class, names = {"OFFICER", "MANAGER"})
        void onlyTheClaimantCanProvideInformation(Role staff) {
            assertThatThrownBy(() -> ClaimLifecycle.transition(AWAITING_INFO, PROVIDE_INFO, staff))
                    .isInstanceOf(ClaimActionNotPermittedException.class);
        }

        @ParameterizedTest
        @EnumSource(value = ClaimAction.class, names = "PROVIDE_INFO", mode = EnumSource.Mode.EXCLUDE)
        void officerAndManagerCanPerformEveryStaffAction(ClaimAction action) {
            assertThat(ClaimLifecycle.transition(action.from(), action, Role.OFFICER)).isEqualTo(action.to());
            assertThat(ClaimLifecycle.transition(action.from(), action, Role.MANAGER)).isEqualTo(action.to());
        }

        @Test
        void permissionIsCheckedBeforeState() {
            // A claimant trying to approve a settled claim gets "not permitted", not a state hint.
            assertThatThrownBy(() -> ClaimLifecycle.transition(SETTLED, APPROVE, Role.CLAIMANT))
                    .isInstanceOf(ClaimActionNotPermittedException.class);
        }

        @Test
        void allowedRolesCannotBeModifiedByCallers() {
            ASSIGN.allowedRoles().add(Role.CLAIMANT);
            assertThat(ASSIGN.isAllowedFor(Role.CLAIMANT)).isFalse();
        }
    }

    @Nested
    class AvailableActions {

        @Test
        void officerDuringAssessmentCanRequestInfoApproveOrReject() {
            assertThat(ClaimLifecycle.availableActions(UNDER_ASSESSMENT, Role.OFFICER))
                    .containsExactlyInAnyOrder(REQUEST_INFO, APPROVE, REJECT);
        }

        @Test
        void claimantOnlyActsWhenInformationIsRequested() {
            for (ClaimStatus status : ClaimStatus.values()) {
                var expected = status == AWAITING_INFO ? EnumSet.of(PROVIDE_INFO) : EnumSet.noneOf(ClaimAction.class);
                assertThat(ClaimLifecycle.availableActions(status, Role.CLAIMANT)).as("%s", status).isEqualTo(expected);
            }
        }

        @Test
        void officerHasNothingToDoWhileWaitingForClaimant() {
            assertThat(ClaimLifecycle.availableActions(AWAITING_INFO, Role.OFFICER)).isEmpty();
        }
    }
}
