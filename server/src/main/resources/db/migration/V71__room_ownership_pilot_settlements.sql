CREATE TABLE room_ownership_pilot_settlements (
    match_id UUID PRIMARY KEY REFERENCES matches (id) ON DELETE CASCADE,
    room_id UUID NOT NULL,
    completion_sha256 TEXT NOT NULL CHECK (completion_sha256 ~ '^[0-9a-f]{64}$')
);
