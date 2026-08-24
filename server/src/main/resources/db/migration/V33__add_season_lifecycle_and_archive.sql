ALTER TABLE seasons
    ADD COLUMN season_number INTEGER,
    ADD COLUMN closed_at TIMESTAMPTZ;

WITH numbered AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY starts_at, id)::INTEGER AS season_number
    FROM seasons
)
UPDATE seasons s
SET season_number = numbered.season_number
FROM numbered
WHERE numbered.id = s.id;

ALTER TABLE seasons
    ALTER COLUMN season_number SET NOT NULL,
    ADD CONSTRAINT seasons_number_unique UNIQUE (season_number),
    ADD CONSTRAINT seasons_number_positive CHECK (season_number > 0);

CREATE TABLE season_leaderboard_archive (
    season_id UUID NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    final_rank INTEGER NOT NULL CHECK (final_rank > 0),
    display_name VARCHAR(32) NOT NULL,
    player_code VARCHAR(10) NOT NULL,
    avatar_url VARCHAR(128),
    frame_id VARCHAR(32) NOT NULL DEFAULT 'frame_default',
    wins INTEGER NOT NULL DEFAULT 0 CHECK (wins >= 0),
    total_matches INTEGER NOT NULL DEFAULT 0 CHECK (total_matches >= 0),
    highest_score INTEGER NOT NULL DEFAULT 0 CHECK (highest_score >= 0),
    rating INTEGER NOT NULL CHECK (rating >= 100),
    season_matches INTEGER NOT NULL CHECK (season_matches >= 0),
    archived_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (season_id, user_id),
    CONSTRAINT season_archive_rank_unique UNIQUE (season_id, final_rank)
);

CREATE INDEX season_leaderboard_archive_rank_idx
    ON season_leaderboard_archive (season_id, final_rank);

CREATE INDEX seasons_closed_idx
    ON seasons (closed_at, ends_at DESC);
