package com.example.claims.claim;

import com.example.claims.common.web.ApiExceptions.ConflictException;

/** A permitted action attempted from the wrong state. Maps to HTTP 409 Conflict. */
public class InvalidClaimTransitionException extends ConflictException {

    private final ClaimStatus currentStatus;
    private final ClaimAction action;

    public InvalidClaimTransitionException(ClaimStatus currentStatus, ClaimAction action) {
        super("Cannot " + action + " a claim in status " + currentStatus
                + " (requires " + action.from() + ")");
        this.currentStatus = currentStatus;
        this.action = action;
    }

    public ClaimStatus currentStatus() {
        return currentStatus;
    }

    public ClaimAction action() {
        return action;
    }
}
