package com.example.claims.outbox;

import com.example.claims.TestcontainersConfiguration;
import com.example.claims.claim.ClaimCommandService;
import com.example.claims.claim.ClaimRequests;
import com.example.claims.claim.ClaimStatus;
import com.example.claims.claim.ClaimRepository;
import com.example.claims.claim.Decision;
import com.example.claims.common.events.ClaimEvent;
import com.example.claims.common.events.ClaimEventType;
import com.example.claims.common.web.ApiExceptions.BusinessRuleException;
import com.example.claims.user.CurrentUser;
import com.example.claims.user.Role;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.claims.SeedData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Outbox writer guarantees and the poller against a real broker. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OutboxIntegrationTest {

    static final CurrentUser ALICE_USER = new CurrentUser(UUID.fromString(ALICE), "alice", "Alice Tan", Role.CLAIMANT);
    static final CurrentUser OLIVIA_USER = new CurrentUser(UUID.fromString(OLIVIA), "olivia", "Olivia Lim", Role.OFFICER);

    @Autowired
    ClaimCommandService commands;
    @Autowired
    ClaimRepository claims;
    @Autowired
    OutboxRepository outbox;
    @Autowired
    OutboxWriter writer;
    @Autowired
    OutboxPoller poller;
    @Autowired
    KafkaContainer kafka;

    @Test
    void writerRefusesToRunOutsideATransaction() {
        var event = new ClaimEvent(UUID.randomUUID(), ClaimEventType.CLAIM_SUBMITTED, UUID.randomUUID(), 0,
                Instant.now(), null, new ClaimEvent.Payload(null, Map.of()));
        assertThatThrownBy(() -> writer.append(event)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void businessRuleFailureAfterStateChangeRollsBackClaimAndEvent() {
        UUID id = underAssessment();
        var tooMuch = new ClaimRequests.Decide(Decision.Outcome.APPROVED, new BigDecimal("999999.00"), "ok");

        assertThatThrownBy(() -> commands.decide(id, tooMuch, OLIVIA_USER)).isInstanceOf(BusinessRuleException.class);

        assertThat(claims.findById(id).orElseThrow().getStatus()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);
        assertThat(outbox.findByAggregateIdOrderBySeq(id)).extracting(OutboxEvent::getEventType)
                .doesNotContain("ClaimApproved");
    }

    @Test
    void pollerPublishesEventsToKafkaInOrderKeyedByClaimId() {
        UUID id = underAssessment();

        drainOutbox();

        assertThat(outbox.findByAggregateIdOrderBySeq(id)).allSatisfy(e -> {
            assertThat(e.getPublishedAt()).isNotNull();
            assertThat(e.getAttempts()).isEqualTo(1);
        });

        List<ConsumerRecord<String, String>> received = consume(id, 3);
        assertThat(received).extracting(r -> (String) JsonPath.read(r.value(), "$.eventType"))
                .containsExactly("ClaimSubmitted", "ClaimAssigned", "ClaimAssessmentStarted");
        assertThat(received).extracting(r -> ((Number) JsonPath.read(r.value(), "$.claimVersion")).intValue())
                .containsExactly(0, 1, 2);
        assertThat(received).extracting(ConsumerRecord::partition).containsOnly(received.get(0).partition());
    }

    @Test
    void publishedEventsAreNotSentAgain() {
        underAssessment();
        drainOutbox();
        assertThat(poller.publishBatch()).isZero();
    }

    private UUID underAssessment() {
        UUID id = commands.submit(new ClaimRequests.SubmitClaim(UUID.fromString(ALICE_SG_POLICY),
                LocalDate.parse("2026-11-01"), "Hail damage", new BigDecimal("1500.00")), ALICE_USER);
        commands.assign(id, null, OLIVIA_USER);
        commands.startAssessment(id, OLIVIA_USER);
        return id;
    }

    private void drainOutbox() {
        while (poller.publishBatch() > 0) {
            // other test classes may have left unpublished rows; keep going until empty
        }
    }

    private List<ConsumerRecord<String, String>> consume(UUID claimId, int expected) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("claim-events"));
            long deadline = System.currentTimeMillis() + 20_000;
            while (matching.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (claimId.toString().equals(r.key())) {
                        matching.add(r);
                    }
                });
            }
        }
        return matching;
    }
}
