ALTER TABLE clan_join_requests
    DROP CONSTRAINT IF EXISTS clan_join_requests_user_id_key;

CREATE INDEX IF NOT EXISTS clan_join_requests_user_created_idx
    ON clan_join_requests(user_id, requested_at DESC);
