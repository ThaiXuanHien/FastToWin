ALTER TABLE matches ADD COLUMN event_rewards JSONB;
ALTER TABLE match_players ADD COLUMN reward_breakdown JSONB;
-- Existing receipts are final. Only explicit prepared starts may be recovered/refunded.
ALTER TABLE play_quota_match_consumptions
    ADD COLUMN reservation_state VARCHAR(16) NOT NULL DEFAULT 'FINALIZED'
    CHECK (reservation_state IN ('RESERVED', 'FINALIZED'));
