package com.example.claims.user;

/** Stored on app_user.role. The server resolves it from the DB, never from the request. */
public enum Role {
    CLAIMANT,
    OFFICER,
    MANAGER
}
