package com.example.claims.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    private UUID id;

    /** DB identity column; defines publish order. Read-only from Java. */
    @Column(insertable = false, updatable = false)
    private Long seq;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    /** Full JSON envelope, sent to Kafka as-is. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    protected OutboxEvent() {
    }

    OutboxEvent(UUID id, UUID aggregateId, String eventType, String payload, Instant createdAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    void markPublished(Instant now) {
        this.attempts++;
        this.publishedAt = now;
        this.lastError = null;
    }

    void markFailed(String error) {
        this.attempts++;
        this.lastError = error == null ? "unknown error"
                : error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }

    public UUID getId() {
        return id;
    }

    public Long getSeq() {
        return seq;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
