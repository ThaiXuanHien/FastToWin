ALTER TABLE clans
    ADD COLUMN experience_points BIGINT NOT NULL DEFAULT 0 CHECK (experience_points >= 0),
    ADD COLUMN level INTEGER NOT NULL DEFAULT 1 CHECK (level BETWEEN 1 AND 50),
    ADD COLUMN donated_gold BIGINT NOT NULL DEFAULT 0 CHECK (donated_gold >= 0),
    ADD COLUMN donated_gems BIGINT NOT NULL DEFAULT 0 CHECK (donated_gems >= 0),
    ADD COLUMN level_reached_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE clan_members
    ADD COLUMN donated_gold BIGINT NOT NULL DEFAULT 0 CHECK (donated_gold >= 0),
    ADD COLUMN donated_gems BIGINT NOT NULL DEFAULT 0 CHECK (donated_gems >= 0);

CREATE TABLE clan_donations (
    request_id UUID PRIMARY KEY,
    clan_id UUID NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    currency VARCHAR(8) NOT NULL CHECK (currency IN ('GOLD', 'GEMS')),
    amount INTEGER NOT NULL CHECK (amount > 0),
    experience_granted INTEGER NOT NULL CHECK (experience_granted > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX clan_donations_clan_history_idx
    ON clan_donations (clan_id, created_at DESC, request_id);

CREATE INDEX clan_donations_member_history_idx
    ON clan_donations (user_id, created_at DESC, request_id);

CREATE INDEX clans_level_leaderboard_idx
    ON clans (level DESC, experience_points DESC, level_reached_at, id);

CREATE INDEX clans_gold_donation_leaderboard_idx
    ON clans (donated_gold DESC, level DESC, id);

CREATE INDEX clans_gem_donation_leaderboard_idx
    ON clans (donated_gems DESC, level DESC, id);
