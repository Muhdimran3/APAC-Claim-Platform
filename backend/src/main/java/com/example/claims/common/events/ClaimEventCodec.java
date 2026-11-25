package com.example.claims.common.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * The one place that turns a {@link ClaimEvent} into the JSON string on Kafka and back.
 * The outbox writer serialises with it and every projection consumer parses with it.
 */
@Component
public class ClaimEventCodec {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventCodec.class);

    private final JsonMapper jsonMapper;
    /** Amounts in {@code details} stay exact (BigDecimal, not double). */
    private final ObjectReader reader;

    public ClaimEventCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.reader = jsonMapper.readerFor(ClaimEvent.class)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    public String write(ClaimEvent event) {
        return jsonMapper.writeValueAsString(event);
    }

    /**
     * Parses a message value. A message that can't be parsed (corrupt, or an event type this
     * version doesn't know) is logged and returned empty. Retrying it would never succeed and
     * would block every later event on the same partition.
     */
    public Optional<ClaimEvent> tryRead(String json) {
        try {
            ClaimEvent event = reader.readValue(json);
            if (event == null || event.eventId() == null || event.claimId() == null || event.eventType() == null
                    || event.payload() == null || event.payload().snapshot() == null) {
                log.error("Skipping claim event with missing required fields: {}", abbreviate(json));
                return Optional.empty();
            }
            return Optional.of(event);
        } catch (JacksonException e) {
            log.error("Skipping unparseable claim event: {}", abbreviate(json), e);
            return Optional.empty();
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }
}
