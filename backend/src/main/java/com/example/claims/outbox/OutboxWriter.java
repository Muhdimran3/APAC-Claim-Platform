package com.example.claims.outbox;

import com.example.claims.common.events.ClaimEvent;
import com.example.claims.common.events.ClaimEventCodec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an event to the outbox table. MANDATORY propagation means this fails fast unless
 * called inside the transaction that changes the claim, so state and event commit or roll
 * back together.
 */
@Component
public class OutboxWriter {

    private final OutboxRepository repository;
    private final ClaimEventCodec codec;

    public OutboxWriter(OutboxRepository repository, ClaimEventCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(ClaimEvent event) {
        repository.save(new OutboxEvent(
                event.eventId(),
                event.claimId(),
                event.eventType().wireName(),
                codec.write(event),
                event.occurredAt()));
    }
}
