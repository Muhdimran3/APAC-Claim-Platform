package com.example.claims.claim;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Command inputs for the claim API: request bodies and service parameters. Bean validation
 * checks shape (400); business rules that need data, like policy period or sum insured, are
 * checked in {@link ClaimCommandService} (422).
 */
public final class ClaimRequests {

    private ClaimRequests() {
    }

    public record SubmitClaim(
            @NotNull UUID policyId,
            @NotNull @PastOrPresent LocalDate incidentDate,
            @NotBlank @Size(max = 4000) String description,
            @NotNull @Positive @Digits(integer = 16, fraction = 2) BigDecimal claimedAmount) {
    }

    /** officerId: required for managers; officers may omit it (self pick-up). */
    public record AssignClaim(UUID officerId) {
    }

    public record RequestInfo(@NotBlank @Size(max = 2000) String question) {
    }

    public record ProvideInfo(@NotBlank @Size(max = 4000) String response) {
    }

    public record ChangeReserve(
            @NotNull @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record Decide(
            @NotNull Decision.Outcome outcome,
            @Positive @Digits(integer = 16, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 1000) String reason) {

        @AssertTrue(message = "amount is required when approving and must be omitted when rejecting")
        public boolean isAmountValidForOutcome() {
            if (outcome == null) {
                return true; // reported by @NotNull
            }
            return outcome == Decision.Outcome.APPROVED ? amount != null : amount == null;
        }
    }

    public record Settle(@NotNull @Positive @Digits(integer = 16, fraction = 2) BigDecimal amount) {
    }

    public record AddNote(@NotBlank @Size(max = 4000) String body) {
    }
}
