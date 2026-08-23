ALTER TABLE daily_check_ins
    ADD COLUMN reward_gold INTEGER NOT NULL DEFAULT 0 CHECK (reward_gold >= 0),
    ADD COLUMN reward_gems INTEGER NOT NULL DEFAULT 0 CHECK (reward_gems >= 0);

ALTER TABLE user_missions
    ADD COLUMN reward_gold INTEGER NOT NULL DEFAULT 0 CHECK (reward_gold >= 0),
    ADD COLUMN reward_gems INTEGER NOT NULL DEFAULT 0 CHECK (reward_gems >= 0);

UPDATE user_missions
SET reward_xp = CASE mission_code
        WHEN 'DAILY_PLAY_3' THEN 20
        WHEN 'DAILY_WIN_1' THEN 25
        WHEN 'WEEKLY_CORRECT_100' THEN 75
        WHEN 'WEEKLY_PERFECT_1' THEN 120
        ELSE reward_xp
    END,
    reward_gold = CASE mission_code
        WHEN 'DAILY_PLAY_3' THEN 100
        WHEN 'DAILY_WIN_1' THEN 150
        WHEN 'WEEKLY_CORRECT_100' THEN 400
        WHEN 'WEEKLY_PERFECT_1' THEN 600
        ELSE reward_gold
    END,
    reward_gems = CASE mission_code
        WHEN 'WEEKLY_PERFECT_1' THEN 2
        ELSE reward_gems
    END
WHERE claimed_at IS NULL;

ALTER TABLE clans
    ADD COLUMN quest_reward_xp INTEGER NOT NULL DEFAULT 100 CHECK (quest_reward_xp >= 0),
    ADD COLUMN quest_reward_gems INTEGER NOT NULL DEFAULT 0 CHECK (quest_reward_gems >= 0);

CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    gold_delta INTEGER NOT NULL DEFAULT 0,
    gems_delta INTEGER NOT NULL DEFAULT 0,
    xp_delta INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT wallet_transaction_has_value CHECK (
        gold_delta <> 0 OR gems_delta <> 0 OR xp_delta <> 0
    ),
    CONSTRAINT wallet_transaction_source_unique UNIQUE (user_id, source_type, source_id)
);
CREATE INDEX wallet_transactions_user_history_idx
    ON wallet_transactions (user_id, created_at DESC);

-- Store receipts will be written only after Google Play/App Store verification.
CREATE TABLE store_purchases (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store VARCHAR(16) NOT NULL CHECK (store IN ('GOOGLE_PLAY', 'APP_STORE')),
    product_id VARCHAR(128) NOT NULL,
    transaction_id VARCHAR(256) NOT NULL,
    gems_granted INTEGER NOT NULL CHECK (gems_granted > 0),
    verified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT store_purchase_transaction_unique UNIQUE (store, transaction_id)
);
