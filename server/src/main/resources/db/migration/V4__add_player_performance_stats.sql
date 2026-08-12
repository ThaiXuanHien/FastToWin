ALTER TABLE player_stats
    ADD COLUMN correct_selections BIGINT NOT NULL DEFAULT 0 CHECK (correct_selections >= 0),
    ADD COLUMN wrong_selections BIGINT NOT NULL DEFAULT 0 CHECK (wrong_selections >= 0),
    ADD COLUMN reaction_time_total_ms BIGINT NOT NULL DEFAULT 0 CHECK (reaction_time_total_ms >= 0),
    ADD COLUMN reaction_samples BIGINT NOT NULL DEFAULT 0 CHECK (reaction_samples >= 0);
