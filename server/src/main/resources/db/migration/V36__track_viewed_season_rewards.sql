ALTER TABLE season_reward_claims
    ADD COLUMN viewed_at TIMESTAMPTZ;

CREATE INDEX season_reward_claims_pending_view_idx
    ON season_reward_claims (user_id, awarded_at DESC)
    WHERE viewed_at IS NULL;
