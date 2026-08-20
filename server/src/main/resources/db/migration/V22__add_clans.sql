CREATE TABLE clans (
    id UUID PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    description TEXT,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trophies INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE clan_members (
    clan_id UUID NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL CHECK (role IN ('LEADER', 'CO_LEADER', 'MEMBER')),
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (clan_id, user_id)
);

CREATE UNIQUE INDEX idx_clan_members_user ON clan_members (user_id);
