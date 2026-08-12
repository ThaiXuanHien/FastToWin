CREATE TABLE match_events (
    id BIGSERIAL PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_id VARCHAR(64) NOT NULL,
    number INTEGER NOT NULL,
    expected_number INTEGER NOT NULL,
    result VARCHAR(8) NOT NULL CHECK (result IN ('ACCEPTED', 'REJECTED')),
    occurred_at TIMESTAMPTZ NOT NULL,
    sequence INTEGER NOT NULL CHECK (sequence > 0),
    UNIQUE (match_id, user_id, request_id)
);

CREATE INDEX match_events_match_sequence_idx ON match_events(match_id, sequence);
CREATE INDEX match_events_player_time_idx ON match_events(user_id, occurred_at DESC);
