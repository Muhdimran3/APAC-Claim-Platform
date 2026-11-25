package com.example.claims.common.events;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimEventCodecTest {

    private final ClaimEventCodec codec = new ClaimEventCodec(JsonMapper.builder().build());

    @Test
    void roundTripKeepsWireNameVersionAndExactAmounts() {
        ClaimEvent original = TestEvents.claim(UUID.randomUUID(), "SGD", Instant.parse("2026-11-01T00:00:00Z"))
                .reserve("2400.10")
                .next(ClaimEventType.CLAIM_SUBMITTED, Instant.parse("2026-11-01T00:00:00Z"),
                        "claimedAmount", new BigDecimal("2500.10"));

        String json = codec.write(original);
        assertThat(json).contains("\"eventType\":\"ClaimSubmitted\"");

        ClaimEvent parsed = codec.tryRead(json).orElseThrow();
        assertThat(parsed.eventId()).isEqualTo(original.eventId());
        assertThat(parsed.eventType()).isEqualTo(ClaimEventType.CLAIM_SUBMITTED);
        assertThat(parsed.claimVersion()).isZero();
        assertThat(parsed.occurredAt()).isEqualTo(original.occurredAt());
        assertThat(parsed.payload().snapshot().reserveAmount()).isEqualByComparingTo("2400.10");
        // Floats in the untyped details map stay BigDecimal, not double.
        assertThat(parsed.payload().details().get("claimedAmount")).isEqualTo(new BigDecimal("2500.10"));
    }

    @Test
    void readsKeysInAnyOrder() {
        // Postgres jsonb reorders keys, so the poller sends a reordered (but equivalent) document.
        String id = UUID.randomUUID().toString();
        String json = """
                {"payload": {"details": {}, "snapshot": {"status": "SUBMITTED", "currency": "SGD"}},
                 "eventType": "ClaimSubmitted", "claimVersion": 0, "claimId": "%s", "eventId": "%s",
                 "occurredAt": "2026-11-01T00:00:00Z"}""".formatted(id, id);
        assertThat(codec.tryRead(json)).isPresent();
    }

    @Test
    void corruptOrUnknownMessagesAreSkippedNotThrown() {
        assertThat(codec.tryRead("{not json")).isEmpty();
        assertThat(codec.tryRead("{\"eventType\": \"SomethingNew\"}")).isEmpty();
        assertThat(codec.tryRead("{}")).isEmpty(); // parses, but required fields missing
        assertThat(codec.tryRead("null")).isEmpty();
    }
}
