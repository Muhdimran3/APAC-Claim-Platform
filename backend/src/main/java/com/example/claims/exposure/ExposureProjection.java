package com.example.claims.exposure;

import com.example.claims.common.events.ClaimEvent;
import com.example.claims.common.events.ClaimEventCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Keeps exposure_claim_view up to date from claim-events. Own consumer group; event data only. */
@Component
class ExposureProjection {

    static final String GROUP = "exposure-projection";
    private static final Logger log = LoggerFactory.getLogger(ExposureProjection.class);

    private final ClaimEventCodec codec;
    private final ExposureViewRepository views;

    ExposureProjection(ClaimEventCodec codec, ExposureViewRepository views) {
        this.codec = codec;
        this.views = views;
    }

    @KafkaListener(topics = "${app.kafka.topics.claim-events}", groupId = GROUP)
    void onMessage(String value) {
        codec.tryRead(value).ifPresent(this::apply);
    }

    boolean apply(ClaimEvent event) {
        boolean applied = views.upsert(event);
        if (!applied) {
            log.debug("Exposure ignored {} v{} for claim {} (duplicate or stale)",
                    event.eventType(), event.claimVersion(), event.claimId());
        }
        return applied;
    }
}
