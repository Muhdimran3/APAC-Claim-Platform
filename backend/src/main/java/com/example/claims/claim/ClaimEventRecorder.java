package com.example.claims.claim;

import com.example.claims.common.events.ClaimEvent;
import com.example.claims.common.events.ClaimEventType;
import com.example.claims.outbox.OutboxWriter;
import com.example.claims.policy.Policy;
import com.example.claims.policy.PolicyRepository;
import com.example.claims.user.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Builds the event envelope from the claim's post-change state and appends it to the outbox.
 * Call after the claim has been flushed, so {@code claim.getVersion()} is the new version.
 */
@Component
class ClaimEventRecorder {

    private final OutboxWriter outbox;
    private final PolicyRepository policies;

    ClaimEventRecorder(OutboxWriter outbox, PolicyRepository policies) {
        this.outbox = outbox;
        this.policies = policies;
    }

    void record(ClaimEventType type, Claim claim, CurrentUser actor, Map<String, Object> details) {
        Policy policy = policies.findById(claim.getPolicyId())
                .orElseThrow(() -> new IllegalStateException("Policy missing for claim " + claim.getId()));
        var snapshot = new ClaimEvent.Snapshot(
                claim.getClaimNumber(),
                claim.getStatus().name(),
                claim.getClaimantId(),
                claim.getAssignedOfficerId(),
                policy.getMarket().name(),
                policy.getProduct().name(),
                claim.getCurrency(),
                claim.getReserveAmount(),
                claim.getSubmittedAt(),
                claim.getClosedAt());
        outbox.append(new ClaimEvent(
                UUID.randomUUID(),
                type,
                claim.getId(),
                claim.getVersion(),
                claim.getUpdatedAt(),
                actor.id(),
                new ClaimEvent.Payload(snapshot, details)));
    }
}
