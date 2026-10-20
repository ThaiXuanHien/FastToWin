CREATE TABLE admob_rewarded_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_id VARCHAR(64) NOT NULL,
    custom_data VARCHAR(64) NOT NULL UNIQUE,
    ad_unit_id VARCHAR(64) NOT NULL,
    created_at_millis BIGINT NOT NULL,
    expires_at_millis BIGINT NOT NULL,
    provider_transaction_id VARCHAR(256) UNIQUE,
    provider_event_at_millis BIGINT,
    UNIQUE (user_id, request_id),
    CHECK (expires_at_millis > created_at_millis),
    CHECK ((provider_transaction_id IS NULL) = (provider_event_at_millis IS NULL))
);
CREATE INDEX admob_rewarded_sessions_pending_owner_idx
    ON admob_rewarded_sessions (user_id, expires_at_millis) WHERE provider_transaction_id IS NULL;
