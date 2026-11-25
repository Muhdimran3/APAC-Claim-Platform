package com.example.claims.claim;

import com.example.claims.common.web.ApiExceptions.ForbiddenException;
import com.example.claims.user.Role;

/** The caller's role may never perform this action. Maps to HTTP 403 Forbidden. */
public class ClaimActionNotPermittedException extends ForbiddenException {

    private final ClaimAction action;
    private final Role role;

    public ClaimActionNotPermittedException(ClaimAction action, Role role) {
        super("Role " + role + " is not permitted to " + action);
        this.action = action;
        this.role = role;
    }

    public ClaimAction action() {
        return action;
    }

    public Role role() {
        return role;
    }
}
