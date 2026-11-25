-- The timeline module authorises claimants without reading the claim tables: it keeps the
-- claimant id from each event's snapshot. Safe as NOT NULL because no consumer has written
-- timeline rows before this migration.
ALTER TABLE claim_timeline_entry ADD COLUMN claimant_id UUID NOT NULL;
CREATE INDEX idx_timeline_claimant ON claim_timeline_entry (claimant_id);
