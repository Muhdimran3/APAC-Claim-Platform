package com.example.claims.claim;

import com.example.claims.policy.Policy;
import com.example.claims.user.CurrentUser;
import com.example.claims.user.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Builds in-memory claims in a given state for unit tests (no DB). */
final class ClaimTestData {

    static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    static final CurrentUser ALICE = new CurrentUser(UUID.randomUUID(), "alice", "Alice", Role.CLAIMANT);
    static final CurrentUser BEN = new CurrentUser(UUID.randomUUID(), "ben", "Ben", Role.CLAIMANT);
    static final CurrentUser OLIVIA = new CurrentUser(UUID.randomUUID(), "olivia", "Olivia", Role.OFFICER);
    static final CurrentUser OSCAR = new CurrentUser(UUID.randomUUID(), "oscar", "Oscar", Role.OFFICER);
    static final CurrentUser MAYA = new CurrentUser(UUID.randomUUID(), "maya", "Maya", Role.MANAGER);

    private ClaimTestData() {
    }

    static Claim submittedBy(CurrentUser claimant) {
        Policy policy = mock(Policy.class);
        when(policy.getId()).thenReturn(UUID.randomUUID());
        when(policy.getCurrency()).thenReturn("SGD");
        return Claim.submit("CLM-000001", policy, claimant.id(), LocalDate.parse("2026-11-01"),
                "Rear-ended", new BigDecimal("2500.00"), NOW);
    }

    /** ALICE's claim, assigned to OLIVIA and under assessment. */
    static Claim underAssessment() {
        Claim c = submittedBy(ALICE);
        c.assignTo(OLIVIA.id(), Role.OFFICER, NOW);
        c.startAssessment(Role.OFFICER, NOW);
        return c;
    }
}
