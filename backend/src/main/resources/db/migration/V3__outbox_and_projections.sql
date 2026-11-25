-- Transactional outbox: rows are inserted in the same transaction as the claim change
-- and published to Kafka by a poller. published_at IS NULL = still to send.
CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,                      -- becomes eventId on the Kafka message
    seq          BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,  -- publish order (now() is identical within one tx)
    aggregate_id UUID        NOT NULL,                  -- claimId, used as the Kafka key
    event_type   VARCHAR(50) NOT NULL,
    payload      JSONB       NOT NULL,                  -- full event envelope
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts     INT         NOT NULL DEFAULT 0,
    last_error   TEXT
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (seq) WHERE published_at IS NULL;

-- ---------------------------------------------------------------------------
-- Read models. Written only by Kafka consumers. No foreign keys to the claim
-- tables on purpose: each module could move to its own service/database.
-- last_claim_version makes upserts idempotent and ignores stale/duplicate events.
-- ---------------------------------------------------------------------------

-- workload module
CREATE TABLE workload_claim_view (
    claim_id           UUID PRIMARY KEY,
    claim_number       VARCHAR(20) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    officer_id         UUID,
    market             CHAR(2)     NOT NULL,
    product            VARCHAR(10) NOT NULL,
    submitted_at       TIMESTAMPTZ NOT NULL,
    status_changed_at  TIMESTAMPTZ NOT NULL,
    closed_at          TIMESTAMPTZ,
    last_claim_version BIGINT      NOT NULL
);
CREATE INDEX idx_workload_officer_status ON workload_claim_view (officer_id, status);

-- exposure module: totals are computed with SUM ... GROUP BY on demand, not stored as
-- running counters, so a redelivered event can never double-count.
CREATE TABLE exposure_claim_view (
    claim_id           UUID PRIMARY KEY,
    market             CHAR(2)       NOT NULL,
    product            VARCHAR(10)   NOT NULL,
    currency           CHAR(3)       NOT NULL,
    reserve_amount     NUMERIC(18,2) NOT NULL,
    is_open            BOOLEAN       NOT NULL,
    last_claim_version BIGINT        NOT NULL
);
CREATE INDEX idx_exposure_open_currency ON exposure_claim_view (currency) WHERE is_open;

-- timeline module: one row per event; PK on event_id dedupes redeliveries.
CREATE TABLE claim_timeline_entry (
    event_id            UUID PRIMARY KEY,
    claim_id            UUID         NOT NULL,
    event_type          VARCHAR(50)  NOT NULL,
    occurred_at         TIMESTAMPTZ  NOT NULL,
    actor_id            UUID,
    summary             VARCHAR(500) NOT NULL,
    visible_to_claimant BOOLEAN      NOT NULL
);
CREATE INDEX idx_timeline_claim ON claim_timeline_entry (claim_id, occurred_at);
