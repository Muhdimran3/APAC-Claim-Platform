package com.example.claims.claim;

import com.example.claims.common.web.ApiExceptions.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Officer's question to the claimant and the claimant's answer. */
@Entity
@Table(name = "info_request")
public class InfoRequest {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(nullable = false, updatable = false)
    private String question;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    private String response;

    @Column(name = "responded_by")
    private UUID respondedBy;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected InfoRequest() {
    }

    static InfoRequest open(UUID claimId, UUID requestedBy, String question, Instant now) {
        InfoRequest r = new InfoRequest();
        r.id = UUID.randomUUID();
        r.claimId = claimId;
        r.requestedBy = requestedBy;
        r.question = question;
        r.requestedAt = now;
        return r;
    }

    void answer(String response, UUID respondedBy, Instant now) {
        if (!isOpen()) {
            throw new ConflictException("Information request has already been answered");
        }
        this.response = response;
        this.respondedBy = respondedBy;
        this.respondedAt = now;
    }

    public boolean isOpen() {
        return response == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public String getQuestion() {
        return question;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public String getResponse() {
        return response;
    }

    public UUID getRespondedBy() {
        return respondedBy;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
