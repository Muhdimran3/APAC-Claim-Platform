package com.example.claims.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope for every message on claim-events (Kafka key = claimId).
 * Uses plain strings and UUIDs, not module enums, so consumers depend only on this contract.
 *
 * @param claimVersion the claim's optimistic-lock version after the change; consumers apply an
 *                     event only if it is newer than what they've stored (idempotent, ignores stale)
 */
public record ClaimEvent(
        UUID eventId,
        ClaimEventType eventType,
        UUID claimId,
        long claimVersion,
        Instant occurredAt,
        UUID actorId,
        Payload payload) {

    /**
     * @param snapshot projection-relevant claim state after the change (event-carried state transfer)
     * @param details  event-specific facts for the timeline, e.g. question, reason, amount
     */
    public record Payload(Snapshot snapshot, Map<String, Object> details) {
    }

    public record Snapshot(
            String claimNumber,
            String status,
            UUID claimantId,
            UUID officerId,
            String market,
            String product,
            String currency,
            BigDecimal reserveAmount,
            Instant submittedAt,
            Instant closedAt) {
    }
}
