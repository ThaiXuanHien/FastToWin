CREATE TABLE clan_join_requests (
    clan_id UUID NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, user_id),
    UNIQUE (user_id)
);

CREATE INDEX clan_join_requests_clan_created_idx
    ON clan_join_requests(clan_id, requested_at ASC);
