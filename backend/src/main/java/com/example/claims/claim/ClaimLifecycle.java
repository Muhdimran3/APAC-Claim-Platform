package com.example.claims.claim;

import com.example.claims.user.Role;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Pure lifecycle rules: no I/O or Spring, so they can be unit-tested exhaustively.
 * The service layer calls {@link #transition} before changing a claim.
 */
public final class ClaimLifecycle {

    private ClaimLifecycle() {
    }

    /**
     * Returns the next status, or throws if the action isn't allowed.
     * The role is checked before the state, so a caller without permission learns nothing
     * about the claim's state (maps to 403); a permitted action in the wrong state maps to 409.
     */
    public static ClaimStatus transition(ClaimStatus current, ClaimAction action, Role actor) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actor, "actor");

        if (!action.isAllowedFor(actor)) {
            throw new ClaimActionNotPermittedException(action, actor);
        }
        if (current != action.from()) {
            throw new InvalidClaimTransitionException(current, action);
        }
        return action.to();
    }

    /** Actions this role can take from this state; the frontend uses it to decide which buttons to show. */
    public static Set<ClaimAction> availableActions(ClaimStatus current, Role actor) {
        Set<ClaimAction> result = EnumSet.noneOf(ClaimAction.class);
        for (ClaimAction action : ClaimAction.values()) {
            if (action.from() == current && action.isAllowedFor(actor)) {
                result.add(action);
            }
        }
        return result;
    }

    /** States reachable in one step from this state, regardless of role. */
    public static Set<ClaimStatus> nextStatuses(ClaimStatus current) {
        Set<ClaimStatus> result = EnumSet.noneOf(ClaimStatus.class);
        Arrays.stream(ClaimAction.values())
                .filter(action -> action.from() == current)
                .forEach(action -> result.add(action.to()));
        return result;
    }
}
