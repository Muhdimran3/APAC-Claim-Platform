package com.example.claims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The one approve/reject decision on a claim (DB enforces one per claim). */
@Entity
@Table(name = "decision")
public class Decision {

    public enum Outcome { APPROVED, REJECTED }

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false, unique = true, updatable = false)
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Outcome outcome;

    @Column(name = "approved_amount", updatable = false)
    private BigDecimal approvedAmount;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "decided_by", nullable = false, updatable = false)
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected Decision() {
    }

    Decision(UUID claimId, Outcome outcome, BigDecimal approvedAmount, String reason, UUID decidedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.outcome = outcome;
        this.approvedAmount = approvedAmount;
        this.reason = reason;
        this.decidedBy = decidedBy;
        this.decidedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public String getReason() {
        return reason;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
