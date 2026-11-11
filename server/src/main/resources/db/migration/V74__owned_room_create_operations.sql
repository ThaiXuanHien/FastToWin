-- Historical proof is deliberately independent of room/event retention.
CREATE TABLE owned_room_create_operations (
    operation_id uuid PRIMARY KEY,
    room_id uuid NOT NULL UNIQUE,
    player_id uuid NOT NULL,
    socket_generation bigint NOT NULL CHECK (socket_generation > 0),
    destination_node_id uuid NOT NULL,
    policy_generation bigint NOT NULL CHECK (policy_generation > 0),
    topology_sha256 text NOT NULL CHECK (topology_sha256 ~ '^[0-9a-f]{64}$'),
    policy_sha256 text NOT NULL CHECK (policy_sha256 ~ '^[0-9a-f]{64}$'),
    command_sha256 text NOT NULL CHECK (command_sha256 ~ '^[0-9a-f]{64}$'),
    password_encoding text NOT NULL DEFAULT 'pbkdf2-sha256-120000-v1' CHECK (password_encoding = 'pbkdf2-sha256-120000-v1'),
    password_salt bytea NOT NULL CHECK (octet_length(password_salt) = 16),
    password_hash bytea NOT NULL CHECK (octet_length(password_hash) = 32),
    created_instance_id uuid NOT NULL,
    created_epoch bigint NOT NULL CHECK (created_epoch > 0),
    created_revision bigint NOT NULL CHECK (created_revision > 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    closed_epoch bigint,
    closed_revision bigint,
    closed_at timestamptz,
    CHECK ((closed_at IS NULL AND closed_epoch IS NULL AND closed_revision IS NULL) OR
           (closed_at IS NOT NULL AND closed_epoch IS NOT NULL AND closed_revision IS NOT NULL
            AND closed_epoch >= created_epoch AND closed_revision > created_revision))
);
