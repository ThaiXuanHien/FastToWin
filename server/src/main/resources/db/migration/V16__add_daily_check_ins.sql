ALTER TABLE player_stats
    ADD COLUMN current_daily_check_in_streak INTEGER NOT NULL DEFAULT 0
        CHECK (current_daily_check_in_streak >= 0),
    ADD COLUMN best_daily_check_in_streak INTEGER NOT NULL DEFAULT 0
        CHECK (best_daily_check_in_streak >= 0),
    ADD COLUMN total_daily_check_ins INTEGER NOT NULL DEFAULT 0
        CHECK (total_daily_check_ins >= 0),
    ADD COLUMN last_daily_check_in_date DATE;

CREATE TABLE daily_check_ins (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    check_in_date DATE NOT NULL,
    cycle_day SMALLINT NOT NULL CHECK (cycle_day BETWEEN 1 AND 7),
    reward_xp INTEGER NOT NULL CHECK (reward_xp > 0),
    streak_after INTEGER NOT NULL CHECK (streak_after > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, check_in_date)
);

CREATE INDEX daily_check_ins_history_idx
    ON daily_check_ins (user_id, check_in_date DESC);
