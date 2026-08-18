ALTER TABLE matches
    DROP CONSTRAINT IF EXISTS matches_game_mode_check;

ALTER TABLE matches
    ADD CONSTRAINT matches_game_mode_check CHECK (
        game_mode IN (
            'ORDER', 'RANDOM_TARGET', 'TIME_BONUS', 'SPEED_UP',
            'SURVIVAL', 'COMBO', 'TIME_ATTACK'
        )
    ),
    ADD COLUMN match_type VARCHAR(8) NOT NULL DEFAULT 'CASUAL'
        CHECK (match_type IN ('CASUAL', 'RANKED'));

-- Before this migration every completed online match changed Elo.
UPDATE matches SET match_type = 'RANKED';

ALTER TABLE season_ratings
    ADD COLUMN placement_matches INTEGER NOT NULL DEFAULT 0
        CHECK (placement_matches BETWEEN 0 AND 5),
    ADD COLUMN peak_rating INTEGER NOT NULL DEFAULT 1000
        CHECK (peak_rating >= 100);

UPDATE season_ratings
SET placement_matches = LEAST(matches_played, 5),
    peak_rating = GREATEST(peak_rating, rating);

CREATE INDEX matches_type_ended_at_idx
    ON matches (match_type, ended_at DESC);
