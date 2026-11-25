package com.example.claims.timeline;

import com.example.claims.common.events.ClaimEvent;
import com.example.claims.common.events.ClaimEventCodec;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Appends one timeline entry per event. Deduplicates on eventId rather than claimVersion: a
 * timeline wants every event, not just the latest state, and the eventId key makes
 * redelivery harmless.
 */
@Component
class TimelineProjection {

    static final String GROUP = "timeline-projection";

    private final ClaimEventCodec codec;
    private final TimelineRepository timeline;

    TimelineProjection(ClaimEventCodec codec, TimelineRepository timeline) {
        this.codec = codec;
        this.timeline = timeline;
    }

    @KafkaListener(topics = "${app.kafka.topics.claim-events}", groupId = GROUP)
    void onMessage(String value) {
        codec.tryRead(value).ifPresent(this::apply);
    }

    boolean apply(ClaimEvent event) {
        return timeline.insert(event, TimelineSummaries.describe(event));
    }
}
