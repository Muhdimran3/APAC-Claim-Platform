package com.example.claims.outbox;

import com.example.claims.common.events.ClaimEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an event to the outbox table. MANDATORY propagation means this fails fast unless
 * called inside the transaction that changes the claim, so state and event commit or roll
 * back together.
 */
@Component
public class OutboxWriter {

    private final OutboxRepository repository;
    private final JsonMapper jsonMapper;

    public OutboxWriter(OutboxRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(ClaimEvent event) {
        repository.save(new OutboxEvent(
                event.eventId(),
                event.claimId(),
                event.eventType().wireName(),
                jsonMapper.writeValueAsString(event),
                event.occurredAt()));
    }
}
