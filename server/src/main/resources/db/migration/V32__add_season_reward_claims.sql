CREATE TABLE season_reward_claims (
    season_id UUID NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tier VARCHAR(16) NOT NULL CHECK (
        tier IN ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER', 'CHALLENGER')
    ),
    peak_rating INTEGER NOT NULL CHECK (peak_rating >= 100),
    reward_gold INTEGER NOT NULL DEFAULT 0 CHECK (reward_gold >= 0),
    reward_gems INTEGER NOT NULL DEFAULT 0 CHECK (reward_gems >= 0),
    awarded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (season_id, user_id),
    CHECK (reward_gold > 0 OR reward_gems > 0)
);

CREATE INDEX season_reward_claims_user_awarded_idx
    ON season_reward_claims (user_id, awarded_at DESC);
