ALTER TABLE player_stats
    ADD COLUMN experience_points INTEGER NOT NULL DEFAULT 0 CHECK (experience_points >= 0),
    ADD COLUMN equipped_frame_id VARCHAR(32) NOT NULL DEFAULT 'frame_default',
    ADD COLUMN equipped_title_id VARCHAR(32) NOT NULL DEFAULT 'title_rookie';

CREATE TABLE user_missions (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mission_code VARCHAR(32) NOT NULL,
    period_start DATE NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0 CHECK (progress >= 0),
    target INTEGER NOT NULL CHECK (target > 0),
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, mission_code, period_start)
);

CREATE INDEX user_missions_recent_idx
    ON user_missions (user_id, period_start DESC);

CREATE TABLE seasons (
    id UUID PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    reward_description VARCHAR(128) NOT NULL,
    CHECK (ends_at > starts_at)
);

CREATE TABLE season_ratings (
    season_id UUID NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL DEFAULT 1000 CHECK (rating >= 100),
    matches_played INTEGER NOT NULL DEFAULT 0 CHECK (matches_played >= 0),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (season_id, user_id)
);

CREATE INDEX season_ratings_leaderboard_idx
    ON season_ratings (season_id, rating DESC, matches_played ASC, updated_at ASC);

INSERT INTO seasons (id, name, starts_at, ends_at, reward_description)
VALUES (
    '7a9a6e3c-6979-4f30-a8d8-33f5a8181f01',
    'Mùa Khởi Đầu',
    date_trunc('month', CURRENT_TIMESTAMP),
    date_trunc('month', CURRENT_TIMESTAMP) + INTERVAL '3 months',
    'Khung mùa giải theo bậc xếp hạng cao nhất'
);
