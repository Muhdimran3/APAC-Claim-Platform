package com.example.claims.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds synthetic claim events for projection tests, independent of the write side.
 * Each builder represents one claim; {@link #next} produces its next version.
 */
public final class TestEvents {

    private final UUID claimId = UUID.randomUUID();
    private final UUID claimantId;
    private final String currency;
    private final Instant submittedAt;
    private long version = -1;

    private String status = "SUBMITTED";
    private UUID officerId;
    private BigDecimal reserve = BigDecimal.ZERO;
    private Instant closedAt;

    private TestEvents(UUID claimantId, String currency, Instant submittedAt) {
        this.claimantId = claimantId;
        this.currency = currency;
        this.submittedAt = submittedAt;
    }

    public static TestEvents claim(UUID claimantId, String currency, Instant submittedAt) {
        return new TestEvents(claimantId, currency, submittedAt);
    }

    public UUID claimId() {
        return claimId;
    }

    public TestEvents status(String status) {
        this.status = status;
        return this;
    }

    public TestEvents officer(UUID officerId) {
        this.officerId = officerId;
        return this;
    }

    public TestEvents reserve(String amount) {
        this.reserve = new BigDecimal(amount);
        return this;
    }

    public TestEvents closedAt(Instant closedAt) {
        this.closedAt = closedAt;
        return this;
    }

    /** Next version of this claim (0, 1, 2, ...) with the current builder state as snapshot. */
    public ClaimEvent next(ClaimEventType type, Instant at, Object... detailPairs) {
        version++;
        Map<String, Object> details = new HashMap<>();
        for (int i = 0; i < detailPairs.length; i += 2) {
            details.put((String) detailPairs[i], detailPairs[i + 1]);
        }
        var snapshot = new ClaimEvent.Snapshot("CLM-TEST", status, claimantId, officerId, "SG", "MOTOR",
                currency, reserve, submittedAt, closedAt);
        return new ClaimEvent(UUID.randomUUID(), type, claimId, version, at, officerId == null ? claimantId : officerId,
                new ClaimEvent.Payload(snapshot, details));
    }
}
