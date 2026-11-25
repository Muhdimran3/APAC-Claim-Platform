package com.example.claims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Append-only audit of reserve (estimated liability) changes. */
@Entity
@Table(name = "reserve_change")
public class ReserveChange {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    @Column(name = "previous_amount", nullable = false, updatable = false)
    private BigDecimal previousAmount;

    @Column(name = "new_amount", nullable = false, updatable = false)
    private BigDecimal newAmount;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected ReserveChange() {
    }

    ReserveChange(UUID claimId, BigDecimal previousAmount, BigDecimal newAmount, String reason,
                  UUID changedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.previousAmount = previousAmount;
        this.newAmount = newAmount;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public BigDecimal getPreviousAmount() {
        return previousAmount;
    }

    public BigDecimal getNewAmount() {
        return newAmount;
    }

    public String getReason() {
        return reason;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
