package com.example.claims.claim;

import com.example.claims.common.web.ApiExceptions.ForbiddenException;
import com.example.claims.common.web.ApiExceptions.NotFoundException;
import com.example.claims.user.CurrentUser;
import com.example.claims.user.Role;

import java.util.EnumSet;
import java.util.Set;

/**
 * Who may see or act on a specific claim. Complements {@link ClaimLifecycle}, which only
 * knows roles and states.
 * <ul>
 *   <li>Claimants: own claims only. Another claimant's claim is reported as 404 so its
 *       existence isn't revealed.</li>
 *   <li>Officers: can view any claim, but can only act on claims assigned to them
 *       (except picking up an unassigned claim).</li>
 *   <li>Managers: can view and act on any claim.</li>
 * </ul>
 * Checks run in this order: view (404) → role/assignment (403) → state (409, in the entity).
 */
final class ClaimAccess {

    private ClaimAccess() {
    }

    static void requireView(Claim claim, CurrentUser user) {
        if (user.is(Role.CLAIMANT) && !claim.isOwnedBy(user.id())) {
            throw notFound(claim);
        }
    }

    /** Staff action on an existing assignment (assess, request info, reserve, decide, settle, note). */
    static void requireStaffAction(Claim claim, CurrentUser user) {
        requireView(claim, user);
        switch (user.role()) {
            case CLAIMANT -> throw new ForbiddenException("Only claims staff can do this");
            case OFFICER -> {
                if (!claim.isAssignedTo(user.id())) {
                    throw new ForbiddenException("Claim " + claim.getClaimNumber() + " is not assigned to you");
                }
            }
            case MANAGER -> { /* any claim */ }
        }
    }

    /** Responding to an information request: only the claimant who owns the claim. */
    static void requireOwner(Claim claim, CurrentUser user) {
        requireView(claim, user);
        if (!user.is(Role.CLAIMANT)) {
            throw new ForbiddenException("Only the claimant can respond to an information request");
        }
    }

    /**
     * Lifecycle actions this user can take on this claim now, with ownership and assignment
     * applied. The frontend uses this to decide which buttons to show.
     */
    static Set<ClaimAction> availableActions(Claim claim, CurrentUser user) {
        Set<ClaimAction> byRoleAndState = ClaimLifecycle.availableActions(claim.getStatus(), user.role());
        boolean mayAct = switch (user.role()) {
            case CLAIMANT -> claim.isOwnedBy(user.id());
            case OFFICER -> claim.getAssignedOfficerId() == null || claim.isAssignedTo(user.id());
            case MANAGER -> true;
        };
        return mayAct ? byRoleAndState : EnumSet.noneOf(ClaimAction.class);
    }

    static NotFoundException notFound(Claim claim) {
        return new NotFoundException("Claim " + claim.getId() + " not found");
    }
}
