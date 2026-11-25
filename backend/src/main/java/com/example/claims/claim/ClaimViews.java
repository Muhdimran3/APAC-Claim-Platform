package com.example.claims.claim;

import com.example.claims.policy.Market;
import com.example.claims.policy.Product;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** API response shapes for claims. */
public final class ClaimViews {

    private ClaimViews() {
    }

    public record UserRef(UUID id, String displayName) {
    }

    public record PolicyRef(UUID id, String policyNumber, Market market, Product product) {
    }

    public record ClaimSummary(
            UUID id,
            String claimNumber,
            ClaimStatus status,
            PolicyRef policy,
            String currency,
            BigDecimal claimedAmount,
            LocalDate incidentDate,
            Instant submittedAt,
            Instant updatedAt,
            UserRef claimant,
            UserRef assignedOfficer) {
    }

    /**
     * Fields that are null are left out of the JSON. Claimants never receive reserveAmount,
     * reserveHistory or notes, which are internal liability estimates and staff-only notes.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClaimDetail(
            UUID id,
            String claimNumber,
            ClaimStatus status,
            PolicyRef policy,
            String currency,
            BigDecimal claimedAmount,
            LocalDate incidentDate,
            String description,
            Instant submittedAt,
            Instant updatedAt,
            Instant closedAt,
            UserRef claimant,
            UserRef assignedOfficer,
            BigDecimal settledAmount,
            long version,
            List<InfoRequestView> infoRequests,
            DecisionView decision,
            Set<ClaimAction> availableActions,
            BigDecimal reserveAmount,
            List<ReserveChangeView> reserveHistory,
            List<NoteView> notes) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InfoRequestView(UUID id, String question, UserRef requestedBy, Instant requestedAt,
                                  String response, Instant respondedAt, boolean open) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DecisionView(Decision.Outcome outcome, BigDecimal approvedAmount, String reason,
                               UserRef decidedBy, Instant decidedAt) {
    }

    public record ReserveChangeView(BigDecimal previousAmount, BigDecimal newAmount, String reason,
                                    UserRef changedBy, Instant changedAt) {
    }

    public record NoteView(UUID id, String body, UserRef author, Instant createdAt) {
    }
}
