package com.example.claims.user;

import java.util.UUID;

/**
 * The authenticated caller for one request. Built by {@link CurrentUserArgumentResolver}
 * from the X-User header plus a DB lookup, so the role always comes from the database.
 */
public record CurrentUser(UUID id, String username, String displayName, Role role) {

    static CurrentUser from(AppUser user) {
        return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    public boolean is(Role r) {
        return role == r;
    }

    public boolean isStaff() {
        return role == Role.OFFICER || role == Role.MANAGER;
    }
}
