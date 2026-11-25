package com.example.claims.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Seeded reference data (V4 migration); read-only in this application. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected AppUser() {
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }
}
