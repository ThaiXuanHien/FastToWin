ALTER TABLE player_cosmetics
    DROP CONSTRAINT IF EXISTS player_cosmetics_cosmetic_type_check;

ALTER TABLE player_cosmetics
    ADD CONSTRAINT player_cosmetics_cosmetic_type_check CHECK (
        cosmetic_type IN ('CARD_BACK', 'BOARD_SKIN', 'AVATAR_FRAME', 'EMOJI', 'FRAME', 'TITLE')
    );

ALTER TABLE season_reward_claims
    ADD COLUMN reward_cosmetic_id VARCHAR(32),
    ADD COLUMN reward_cosmetic_type VARCHAR(16);

UPDATE season_reward_claims src
SET reward_cosmetic_id = 'season_' || s.season_number || '_' || LOWER(src.tier),
    reward_cosmetic_type = CASE
        WHEN src.tier IN ('BRONZE', 'SILVER') THEN 'TITLE'
        ELSE 'FRAME'
    END
FROM seasons s
WHERE s.id = src.season_id;

ALTER TABLE season_reward_claims
    ALTER COLUMN reward_cosmetic_id SET NOT NULL,
    ALTER COLUMN reward_cosmetic_type SET NOT NULL,
    ADD CONSTRAINT season_reward_claims_cosmetic_type_check CHECK (
        reward_cosmetic_type IN ('FRAME', 'TITLE')
    );

INSERT INTO player_cosmetics (user_id, cosmetic_id, cosmetic_type, acquired_at)
SELECT user_id, reward_cosmetic_id, reward_cosmetic_type, awarded_at
FROM season_reward_claims
ON CONFLICT (user_id, cosmetic_id) DO NOTHING;
