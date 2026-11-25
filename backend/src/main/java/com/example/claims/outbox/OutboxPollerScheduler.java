package com.example.claims.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the poller on a fixed delay (the next run starts after the previous one finishes, so
 * runs never overlap within an instance). Disabled in tests, which call publishBatch() directly
 * for deterministic results.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
class OutboxPollerScheduler {

    private final OutboxPoller poller;

    OutboxPollerScheduler(OutboxPoller poller) {
        this.poller = poller;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poller.interval-ms:500}")
    void tick() {
        // Drain the backlog quickly, but stop after a partial batch (caught up, or a failure).
        int sent;
        int rounds = 0;
        do {
            sent = poller.publishBatch();
        } while (sent > 0 && ++rounds < 10 && sent == poller.batchSize());
    }
}
