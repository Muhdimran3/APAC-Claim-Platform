package com.example.claims.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes outbox rows to Kafka in seq order, keyed by claimId.
 *
 * <p>Delivery is at-least-once: if the app dies after Kafka acks but before published_at is
 * committed, the event is sent again, so consumers must deduplicate (eventId / claimVersion).
 *
 * <p>On the first failure the batch stops and the remaining rows wait for the next run.
 * Skipping ahead could publish a later event for the same claim before an earlier one.
 *
 * <p>Known limit: with several app instances, SKIP LOCKED lets two pollers take different
 * batches at once, so per-claim order could break across them. Fine for one instance; for
 * more we'd elect one poller as leader or split rows by claimId hash.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String topic;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxPoller(OutboxRepository repository,
                        KafkaTemplate<String, String> kafka,
                        TransactionTemplate tx,
                        Clock clock,
                        @Value("${app.kafka.topics.claim-events}") String topic,
                        @Value("${app.outbox.batch-size:100}") int batchSize,
                        @Value("${app.outbox.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.repository = repository;
        this.kafka = kafka;
        this.tx = tx;
        this.clock = clock;
        this.topic = topic;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    int batchSize() {
        return batchSize;
    }

    /** Publishes one batch; returns how many events were sent. */
    public int publishBatch() {
        Integer sent = tx.execute(status -> {
            List<OutboxEvent> batch = repository.lockNextBatch(batchSize);
            int published = 0;
            for (OutboxEvent event : batch) {
                try {
                    // Wait for the broker ack: only then is it safe to mark the row published.
                    kafka.send(topic, event.getAggregateId().toString(), event.getPayload())
                            .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                    event.markPublished(clock.instant());
                    published++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    event.markFailed("interrupted");
                    break;
                } catch (ExecutionException | TimeoutException | RuntimeException e) {
                    Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                    event.markFailed(cause.toString());
                    log.warn("Outbox publish failed for event {} (seq {}, attempt {}); stopping batch",
                            event.getId(), event.getSeq(), event.getAttempts(), cause);
                    break;
                }
            }
            return published;
        });
        return sent == null ? 0 : sent;
    }
}
