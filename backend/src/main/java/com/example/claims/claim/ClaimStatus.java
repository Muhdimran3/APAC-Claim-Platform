package com.example.claims.claim;

/**
 * Claim lifecycle states. Allowed moves between them are defined by {@link ClaimAction};
 * this enum only knows whether a state is terminal.
 */
public enum ClaimStatus {
    SUBMITTED,
    ASSIGNED,
    UNDER_ASSESSMENT,
    AWAITING_INFO,
    APPROVED,
    SETTLED,
    REJECTED;

    /** No further actions possible. */
    public boolean isTerminal() {
        return this == SETTLED || this == REJECTED;
    }

    /** Counts towards workload and liability exposure (APPROVED-but-unpaid is still open). */
    public boolean isOpen() {
        return !isTerminal();
    }
}
