CREATE TABLE active_room_snapshots (
    room_id UUID PRIMARY KEY,
    state_json JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX active_room_snapshots_updated_idx ON active_room_snapshots(updated_at);
