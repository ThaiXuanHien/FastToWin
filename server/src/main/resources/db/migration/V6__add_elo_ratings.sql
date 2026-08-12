ALTER TABLE player_stats
    ADD COLUMN elo_rating INTEGER NOT NULL DEFAULT 1000 CHECK (elo_rating >= 100);

CREATE TABLE rating_history (
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating_before INTEGER NOT NULL,
    rating_after INTEGER NOT NULL,
    rating_change INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (match_id, user_id)
);

CREATE INDEX player_stats_elo_leaderboard_idx
    ON player_stats (elo_rating DESC, wins DESC, highest_score DESC, updated_at ASC);

CREATE INDEX rating_history_user_time_idx
    ON rating_history (user_id, created_at DESC);
