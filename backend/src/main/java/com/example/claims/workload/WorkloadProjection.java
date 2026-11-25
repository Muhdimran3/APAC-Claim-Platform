package com.example.claims.workload;

import com.example.claims.common.events.ClaimEvent;
import com.example.claims.common.events.ClaimEventCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps workload_claim_view up to date from claim-events. Has its own consumer group, so its
 * offsets (and any failure) are independent of the other projections. Reads only the event,
 * never the claim tables.
 */
@Component
class WorkloadProjection {

    static final String GROUP = "workload-projection";
    private static final Logger log = LoggerFactory.getLogger(WorkloadProjection.class);

    private final ClaimEventCodec codec;
    private final WorkloadViewRepository views;

    WorkloadProjection(ClaimEventCodec codec, WorkloadViewRepository views) {
        this.codec = codec;
        this.views = views;
    }

    @KafkaListener(topics = "${app.kafka.topics.claim-events}", groupId = GROUP)
    void onMessage(String value) {
        codec.tryRead(value).ifPresent(this::apply);
    }

    /** Returns false if the event was a duplicate or older than the stored version. */
    boolean apply(ClaimEvent event) {
        boolean applied = views.upsert(event);
        if (!applied) {
            log.debug("Workload ignored {} v{} for claim {} (duplicate or stale)",
                    event.eventType(), event.claimVersion(), event.claimId());
        }
        return applied;
    }
}
