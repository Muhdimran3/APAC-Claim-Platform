package com.example.claims.timeline;

import com.example.claims.common.events.ClaimEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.example.claims.common.jdbc.JdbcTime.instant;
import static com.example.claims.common.jdbc.JdbcTime.ts;

@Component
public class TimelineRepository {

    private static final RowMapper<Entry> ROW = (rs, i) -> new Entry(
            rs.getObject("event_id", UUID.class),
            rs.getString("event_type"),
            instant(rs, "occurred_at"),
            rs.getObject("actor_id", UUID.class),
            rs.getString("summary"));

    private final JdbcTemplate jdbc;

    TimelineRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One row per event. The event_id primary key turns a redelivery into a no-op. Returns true if inserted. */
    boolean insert(ClaimEvent e, TimelineSummaries.Line line) {
        int rows = jdbc.update("""
                INSERT INTO claim_timeline_entry (event_id, claim_id, claimant_id, event_type, occurred_at,
                                                  actor_id, summary, visible_to_claimant)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING""",
                e.eventId(), e.claimId(), e.payload().snapshot().claimantId(), e.eventType().wireName(),
                ts(e.occurredAt()), e.actorId(), line.summary(), line.visibleToClaimant());
        return rows == 1;
    }

    List<Entry> forStaff(UUID claimId) {
        return jdbc.query("""
                SELECT event_id, event_type, occurred_at, actor_id, summary
                FROM claim_timeline_entry WHERE claim_id = ? ORDER BY occurred_at, event_id""",
                ROW, claimId);
    }

    /** Empty both for "not your claim" and "no events yet", so nothing leaks about other claims. */
    List<Entry> forClaimant(UUID claimId, UUID claimantId) {
        return jdbc.query("""
                SELECT event_id, event_type, occurred_at, actor_id, summary
                FROM claim_timeline_entry
                WHERE claim_id = ? AND claimant_id = ? AND visible_to_claimant
                ORDER BY occurred_at, event_id""",
                ROW, claimId, claimantId);
    }

    public record Entry(UUID eventId, String eventType, Instant occurredAt, UUID actorId, String summary) {
    }
}
