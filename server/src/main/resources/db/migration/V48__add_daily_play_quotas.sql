CREATE TABLE daily_play_quotas (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quota_date DATE NOT NULL,
    matches_consumed INTEGER NOT NULL DEFAULT 0 CHECK (matches_consumed >= 0),
    bonus_matches_granted INTEGER NOT NULL DEFAULT 0 CHECK (bonus_matches_granted >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, quota_date)
);

CREATE TABLE play_quota_match_consumptions (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    match_id UUID NOT NULL,
    quota_date DATE NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, match_id)
);

CREATE INDEX play_quota_match_consumptions_match_idx
    ON play_quota_match_consumptions (match_id);

CREATE TABLE rewarded_ad_grants (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_transaction_id VARCHAR(256) NOT NULL,
    matches_granted INTEGER NOT NULL CHECK (matches_granted = 2),
    verified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, provider_transaction_id)
);

CREATE INDEX rewarded_ad_grants_user_idx
    ON rewarded_ad_grants (user_id, verified_at DESC);
