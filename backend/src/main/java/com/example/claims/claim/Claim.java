package com.example.claims.claim;

import com.example.claims.common.web.ApiExceptions.ConflictException;
import com.example.claims.policy.Policy;
import com.example.claims.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Claim aggregate root. Every status change goes through {@link ClaimLifecycle}, so the entity
 * can't be put into a state the blueprint doesn't allow. Ownership/assignment checks need the
 * caller's identity and live in {@link ClaimAccess}.
 */
@Entity
@Table(name = "claim")
public class Claim {

    @Id
    private UUID id;

    @Column(name = "claim_number", nullable = false, unique = true, updatable = false)
    private String claimNumber;

    @Column(name = "policy_id", nullable = false, updatable = false)
    private UUID policyId;

    @Column(name = "claimant_id", nullable = false, updatable = false)
    private UUID claimantId;

    @Column(name = "assigned_officer_id")
    private UUID assignedOfficerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Column(name = "incident_date", nullable = false, updatable = false)
    private LocalDate incidentDate;

    @Column(nullable = false, updatable = false)
    private String description;

    @Column(name = "claimed_amount", nullable = false, updatable = false)
    private BigDecimal claimedAmount;

    @Column(nullable = false, updatable = false, columnDefinition = "bpchar(3)")
    private String currency;

    @Column(name = "reserve_amount", nullable = false)
    private BigDecimal reserveAmount;

    @Column(name = "settled_amount")
    private BigDecimal settledAmount;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Optimistic lock. Also the per-claim event sequence number (claimVersion on events). */
    @Version
    private long version;

    protected Claim() {
    }

    static Claim submit(String claimNumber, Policy policy, UUID claimantId, LocalDate incidentDate,
                        String description, BigDecimal claimedAmount, Instant now) {
        Claim c = new Claim();
        c.id = UUID.randomUUID();
        c.claimNumber = claimNumber;
        c.policyId = policy.getId();
        c.claimantId = claimantId;
        c.status = ClaimStatus.SUBMITTED;
        c.incidentDate = incidentDate;
        c.description = description;
        c.claimedAmount = claimedAmount;
        c.currency = policy.getCurrency();
        c.reserveAmount = BigDecimal.ZERO;
        c.submittedAt = now;
        c.updatedAt = now;
        return c;
    }

    void assignTo(UUID officerId, Role actor, Instant now) {
        apply(ClaimAction.ASSIGN, actor, now);
        this.assignedOfficerId = Objects.requireNonNull(officerId);
    }

    /** Change of officer without a status change (manager only, checked by the service). */
    void reassignTo(UUID officerId, Instant now) {
        requireOpen("reassign");
        if (officerId.equals(assignedOfficerId)) {
            throw new ConflictException("Claim is already assigned to this officer");
        }
        this.assignedOfficerId = officerId;
        this.updatedAt = now;
    }

    void startAssessment(Role actor, Instant now) {
        apply(ClaimAction.START_ASSESSMENT, actor, now);
    }

    void requestInfo(Role actor, Instant now) {
        apply(ClaimAction.REQUEST_INFO, actor, now);
    }

    void infoProvided(Role actor, Instant now) {
        apply(ClaimAction.PROVIDE_INFO, actor, now);
    }

    /** Approval aligns the reserve to the amount we have agreed to pay. */
    void approve(BigDecimal approvedAmount, Role actor, Instant now) {
        apply(ClaimAction.APPROVE, actor, now);
        this.reserveAmount = approvedAmount;
    }

    /** Rejection releases the reserve: no further liability. */
    void reject(Role actor, Instant now) {
        apply(ClaimAction.REJECT, actor, now);
        this.reserveAmount = BigDecimal.ZERO;
    }

    /** Payment made: liability is no longer outstanding, so the reserve goes to zero. */
    void settle(BigDecimal amount, Role actor, Instant now) {
        apply(ClaimAction.SETTLE, actor, now);
        this.settledAmount = amount;
        this.reserveAmount = BigDecimal.ZERO;
    }

    void changeReserve(BigDecimal amount, Instant now) {
        requireOpen("change the reserve of");
        if (amount.compareTo(reserveAmount) == 0) {
            throw new ConflictException("Reserve is already " + amount);
        }
        this.reserveAmount = amount;
        this.updatedAt = now;
    }

    public boolean isAssignedTo(UUID userId) {
        return userId.equals(assignedOfficerId);
    }

    public boolean isOwnedBy(UUID userId) {
        return claimantId.equals(userId);
    }

    private void apply(ClaimAction action, Role actor, Instant now) {
        this.status = ClaimLifecycle.transition(status, action, actor);
        this.updatedAt = now;
        if (status.isTerminal()) {
            this.closedAt = now;
        }
    }

    private void requireOpen(String verb) {
        if (status.isTerminal()) {
            throw new ConflictException("Cannot " + verb + " a " + status + " claim");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public UUID getClaimantId() {
        return claimantId;
    }

    public UUID getAssignedOfficerId() {
        return assignedOfficerId;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getReserveAmount() {
        return reserveAmount;
    }

    public BigDecimal getSettledAmount() {
        return settledAmount;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public long getVersion() {
        return version;
    }
}
