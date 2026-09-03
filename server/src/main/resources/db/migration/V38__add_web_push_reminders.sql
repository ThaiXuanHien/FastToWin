ALTER TABLE users
    ALTER COLUMN fcm_token TYPE VARCHAR(4096);

WITH duplicated_tokens AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY fcm_token ORDER BY created_at DESC, id) AS token_rank
    FROM users
    WHERE fcm_token IS NOT NULL AND BTRIM(fcm_token) <> ''
)
UPDATE users u
SET fcm_token = NULL
FROM duplicated_tokens d
WHERE u.id = d.id AND d.token_rank > 1;

CREATE UNIQUE INDEX users_fcm_token_unique_idx
    ON users (fcm_token)
    WHERE fcm_token IS NOT NULL;

CREATE TABLE push_reminder_deliveries (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reminder_key VARCHAR(96) NOT NULL,
    delivered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, reminder_key)
);

CREATE INDEX push_reminder_deliveries_created_idx
    ON push_reminder_deliveries (delivered_at DESC);
