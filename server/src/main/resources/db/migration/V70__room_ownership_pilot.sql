CREATE TABLE room_ownership_pilot_rooms (
    room_id uuid PRIMARY KEY,
    instance_id uuid NOT NULL,
    owner_epoch bigint NOT NULL CHECK (owner_epoch > 0),
    revision bigint NOT NULL CHECK (revision > 0),
    lease_until timestamptz NOT NULL,
    recovery_deadline timestamptz,
    closed_at timestamptz,
    snapshot jsonb NOT NULL
);

CREATE INDEX room_ownership_pilot_expiry_idx
    ON room_ownership_pilot_rooms (lease_until, room_id)
    WHERE closed_at IS NULL;

CREATE TABLE room_ownership_pilot_memberships (
    player_id uuid PRIMARY KEY,
    room_id uuid NOT NULL REFERENCES room_ownership_pilot_rooms(room_id) ON DELETE CASCADE
);

CREATE INDEX room_ownership_pilot_memberships_room_idx ON room_ownership_pilot_memberships (room_id);

CREATE TABLE room_ownership_pilot_sessions (
    player_id uuid PRIMARY KEY,
    generation bigint NOT NULL CHECK (generation > 0)
);

CREATE TABLE room_ownership_pilot_events (
    room_id uuid NOT NULL REFERENCES room_ownership_pilot_rooms(room_id) ON DELETE CASCADE,
    revision bigint NOT NULL CHECK (revision > 0),
    owner_epoch bigint NOT NULL CHECK (owner_epoch > 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    events jsonb NOT NULL,
    PRIMARY KEY (room_id, revision)
);


CREATE INDEX room_ownership_pilot_closed_idx
    ON room_ownership_pilot_rooms (closed_at, room_id)
    WHERE closed_at IS NOT NULL;
