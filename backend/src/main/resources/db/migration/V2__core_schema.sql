-- Core write-side schema, owned by the claim module.
-- Business invariants that are cheap to state in SQL are enforced here as a second line
-- of defence behind the Java lifecycle rules.

CREATE TABLE app_user (
    id           UUID PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    role         VARCHAR(20)  NOT NULL CHECK (role IN ('CLAIMANT', 'OFFICER', 'MANAGER')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE policy (
    id             UUID PRIMARY KEY,
    policy_number  VARCHAR(30)   NOT NULL UNIQUE,
    holder_id      UUID          NOT NULL REFERENCES app_user (id),
    market         CHAR(2)       NOT NULL CHECK (market IN ('SG', 'HK', 'MY', 'TH', 'ID', 'AU')),
    product        VARCHAR(10)   NOT NULL CHECK (product IN ('MOTOR', 'PROPERTY')),
    currency       CHAR(3)       NOT NULL,
    sum_insured    NUMERIC(18,2) NOT NULL CHECK (sum_insured > 0),
    effective_from DATE          NOT NULL,
    effective_to   DATE          NOT NULL,
    CONSTRAINT chk_policy_period CHECK (effective_to > effective_from)
);
CREATE INDEX idx_policy_holder ON policy (holder_id);

-- Human-readable claim numbers (CLM-001000, ...); the UUID stays the technical key.
CREATE SEQUENCE claim_number_seq START WITH 1000;

CREATE TABLE claim (
    id                  UUID PRIMARY KEY,
    claim_number        VARCHAR(20)   NOT NULL UNIQUE,
    policy_id           UUID          NOT NULL REFERENCES policy (id),
    claimant_id         UUID          NOT NULL REFERENCES app_user (id),
    assigned_officer_id UUID          REFERENCES app_user (id),
    status              VARCHAR(20)   NOT NULL CHECK (status IN (
                            'SUBMITTED', 'ASSIGNED', 'UNDER_ASSESSMENT', 'AWAITING_INFO',
                            'APPROVED', 'SETTLED', 'REJECTED')),
    incident_date       DATE          NOT NULL,
    description         TEXT          NOT NULL,
    claimed_amount      NUMERIC(18,2) NOT NULL CHECK (claimed_amount > 0),
    currency            CHAR(3)       NOT NULL,          -- copied from policy at submission, never changes
    reserve_amount      NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (reserve_amount >= 0),
    settled_amount      NUMERIC(18,2) CHECK (settled_amount > 0),
    submitted_at        TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    closed_at           TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0, -- optimistic lock; also orders events per claim
    CONSTRAINT chk_claim_officer_after_submit
        CHECK (status = 'SUBMITTED' OR assigned_officer_id IS NOT NULL),
    CONSTRAINT chk_claim_settled_amount
        CHECK (status <> 'SETTLED' OR settled_amount IS NOT NULL),
    CONSTRAINT chk_claim_closed_at
        CHECK ((status IN ('SETTLED', 'REJECTED')) = (closed_at IS NOT NULL))
);
CREATE INDEX idx_claim_claimant ON claim (claimant_id);
CREATE INDEX idx_claim_officer_status ON claim (assigned_officer_id, status);
CREATE INDEX idx_claim_status ON claim (status);

CREATE TABLE info_request (
    id           UUID PRIMARY KEY,
    claim_id     UUID        NOT NULL REFERENCES claim (id),
    requested_by UUID        NOT NULL REFERENCES app_user (id),
    question     TEXT        NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    response     TEXT,
    responded_by UUID        REFERENCES app_user (id),
    responded_at TIMESTAMPTZ,
    CONSTRAINT chk_info_request_answered
        CHECK ((response IS NULL) = (responded_at IS NULL) AND (response IS NULL) = (responded_by IS NULL))
);
CREATE INDEX idx_info_request_claim ON info_request (claim_id);
-- A claim can have at most one unanswered request (matches the single AWAITING_INFO state).
CREATE UNIQUE INDEX uq_info_request_one_open ON info_request (claim_id) WHERE response IS NULL;

CREATE TABLE claim_note (
    id         UUID PRIMARY KEY,
    claim_id   UUID        NOT NULL REFERENCES claim (id),
    author_id  UUID        NOT NULL REFERENCES app_user (id),
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_claim_note_claim ON claim_note (claim_id);

-- Append-only: the current reserve lives on claim.reserve_amount, this is the audit trail.
CREATE TABLE reserve_change (
    id              UUID PRIMARY KEY,
    claim_id        UUID          NOT NULL REFERENCES claim (id),
    previous_amount NUMERIC(18,2) NOT NULL CHECK (previous_amount >= 0),
    new_amount      NUMERIC(18,2) NOT NULL CHECK (new_amount >= 0),
    reason          VARCHAR(500)  NOT NULL,
    changed_by      UUID          NOT NULL REFERENCES app_user (id),
    changed_at      TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_reserve_change_claim ON reserve_change (claim_id);

CREATE TABLE decision (
    id              UUID PRIMARY KEY,
    claim_id        UUID          NOT NULL UNIQUE REFERENCES claim (id),  -- one decision per claim
    outcome         VARCHAR(10)   NOT NULL CHECK (outcome IN ('APPROVED', 'REJECTED')),
    approved_amount NUMERIC(18,2),
    reason          VARCHAR(1000) NOT NULL,
    decided_by      UUID          NOT NULL REFERENCES app_user (id),
    decided_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_decision_amount CHECK (
        (outcome = 'APPROVED' AND approved_amount > 0) OR
        (outcome = 'REJECTED' AND approved_amount IS NULL))
);
