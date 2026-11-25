package com.example.claims.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Oldest unpublished events, row-locked for this transaction. SKIP LOCKED stops a second
     * poller from blocking on (or double-sending) the same rows.
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY seq
            LIMIT :limit
            FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("limit") int limit);

    List<OutboxEvent> findByAggregateIdOrderBySeq(UUID aggregateId);
}
