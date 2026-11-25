package com.example.claims.claim;

import com.example.claims.user.Role;

import java.util.EnumSet;
import java.util.Set;

/**
 * Every status-changing action: the state it starts from, the state it leads to and the
 * roles allowed to perform it. This is the single source of truth for the lifecycle.
 *
 * <p>Creating a claim (-> SUBMITTED) is not an action because there is no "from" state.
 * Ownership rules (claimant owns the claim, officer is the assignee) depend on the claim
 * instance and are checked in the service layer, not here.
 */
public enum ClaimAction {
    ASSIGN(ClaimStatus.SUBMITTED, ClaimStatus.ASSIGNED, Role.OFFICER, Role.MANAGER),
    START_ASSESSMENT(ClaimStatus.ASSIGNED, ClaimStatus.UNDER_ASSESSMENT, Role.OFFICER, Role.MANAGER),
    REQUEST_INFO(ClaimStatus.UNDER_ASSESSMENT, ClaimStatus.AWAITING_INFO, Role.OFFICER, Role.MANAGER),
    PROVIDE_INFO(ClaimStatus.AWAITING_INFO, ClaimStatus.UNDER_ASSESSMENT, Role.CLAIMANT),
    APPROVE(ClaimStatus.UNDER_ASSESSMENT, ClaimStatus.APPROVED, Role.OFFICER, Role.MANAGER),
    REJECT(ClaimStatus.UNDER_ASSESSMENT, ClaimStatus.REJECTED, Role.OFFICER, Role.MANAGER),
    SETTLE(ClaimStatus.APPROVED, ClaimStatus.SETTLED, Role.OFFICER, Role.MANAGER);

    private final ClaimStatus from;
    private final ClaimStatus to;
    private final Set<Role> allowedRoles;

    ClaimAction(ClaimStatus from, ClaimStatus to, Role first, Role... rest) {
        this.from = from;
        this.to = to;
        this.allowedRoles = EnumSet.of(first, rest);
    }

    public ClaimStatus from() {
        return from;
    }

    public ClaimStatus to() {
        return to;
    }

    public Set<Role> allowedRoles() {
        return EnumSet.copyOf(allowedRoles);
    }

    public boolean isAllowedFor(Role role) {
        return allowedRoles.contains(role);
    }
}
