package com.example.claims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Internal staff note. Never returned to claimants and not published as an event. */
@Entity
@Table(name = "claim_note")
public class ClaimNote {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false, updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClaimNote() {
    }

    ClaimNote(UUID claimId, UUID authorId, String body, Instant now) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
