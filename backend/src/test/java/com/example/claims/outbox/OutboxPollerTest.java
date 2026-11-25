package com.example.claims.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Poller rules without Kafka or a DB. The real broker path is covered by OutboxIntegrationTest. */
class OutboxPollerTest {

    private static final String TOPIC = "claim-events";
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private OutboxRepository repository;
    private KafkaTemplate<String, String> kafka;
    private OutboxPoller poller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(OutboxRepository.class);
        kafka = mock(KafkaTemplate.class);
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        poller = new OutboxPoller(repository, kafka, tx, Clock.fixed(NOW, ZoneOffset.UTC), TOPIC, 100, 200);
    }

    @Test
    void publishesInOrderKeyedByClaimAndMarksEachPublished() {
        UUID claimId = UUID.randomUUID();
        OutboxEvent first = event(claimId, "ClaimSubmitted");
        OutboxEvent second = event(claimId, "ClaimAssigned");
        when(repository.lockNextBatch(100)).thenReturn(List.of(first, second));
        when(kafka.send(eq(TOPIC), anyString(), anyString())).thenReturn(acked());

        int sent = poller.publishBatch();

        assertThat(sent).isEqualTo(2);
        InOrder order = inOrder(kafka);
        order.verify(kafka).send(TOPIC, claimId.toString(), first.getPayload());
        order.verify(kafka).send(TOPIC, claimId.toString(), second.getPayload());
        assertThat(List.of(first, second)).allSatisfy(e -> {
            assertThat(e.getPublishedAt()).isEqualTo(NOW);
            assertThat(e.getAttempts()).isEqualTo(1);
        });
    }

    @Test
    void stopsAtFirstFailureSoLaterEventsAreNotPublishedOutOfOrder() {
        OutboxEvent ok = event(UUID.randomUUID(), "ClaimSubmitted");
        OutboxEvent failing = event(UUID.randomUUID(), "ClaimSubmitted");
        OutboxEvent later = event(UUID.randomUUID(), "ClaimSubmitted");
        when(repository.lockNextBatch(100)).thenReturn(List.of(ok, failing, later));
        when(kafka.send(TOPIC, ok.getAggregateId().toString(), ok.getPayload())).thenReturn(acked());
        when(kafka.send(TOPIC, failing.getAggregateId().toString(), failing.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        int sent = poller.publishBatch();

        assertThat(sent).isEqualTo(1);
        assertThat(ok.getPublishedAt()).isEqualTo(NOW);
        assertThat(failing.getPublishedAt()).isNull();
        assertThat(failing.getAttempts()).isEqualTo(1);
        assertThat(failing.getLastError()).contains("broker unavailable");
        verify(kafka, never()).send(TOPIC, later.getAggregateId().toString(), later.getPayload());
        assertThat(later.getAttempts()).isZero();
    }

    @Test
    void brokerThatNeverAcksCountsAsFailure() {
        OutboxEvent e = event(UUID.randomUUID(), "ClaimSubmitted");
        when(repository.lockNextBatch(100)).thenReturn(List.of(e));
        when(kafka.send(eq(TOPIC), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        assertThat(poller.publishBatch()).isZero();
        assertThat(e.getPublishedAt()).isNull();
        assertThat(e.getLastError()).contains("Timeout");
    }

    @Test
    void emptyOutboxSendsNothing() {
        when(repository.lockNextBatch(100)).thenReturn(List.of());
        assertThat(poller.publishBatch()).isZero();
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    private static OutboxEvent event(UUID claimId, String type) {
        UUID id = UUID.randomUUID();
        return new OutboxEvent(id, claimId, type, "{\"eventId\":\"" + id + "\"}", NOW);
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<SendResult<String, String>> acked() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }
}
