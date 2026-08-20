ALTER TABLE player_stats
    ADD COLUMN gold INTEGER NOT NULL DEFAULT 0 CHECK (gold >= 0),
    ADD COLUMN gems INTEGER NOT NULL DEFAULT 0 CHECK (gems >= 0),
    ADD COLUMN equipped_card_back_id VARCHAR(32) NOT NULL DEFAULT 'card_back_default',
    ADD COLUMN equipped_board_skin_id VARCHAR(32) NOT NULL DEFAULT 'board_skin_default';

CREATE TABLE player_cosmetics (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    cosmetic_id VARCHAR(32) NOT NULL,
    cosmetic_type VARCHAR(16) NOT NULL CHECK (cosmetic_type IN ('CARD_BACK', 'BOARD_SKIN', 'AVATAR_FRAME', 'EMOJI')),
    acquired_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, cosmetic_id)
);
