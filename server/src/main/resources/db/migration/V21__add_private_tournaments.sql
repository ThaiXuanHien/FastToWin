CREATE TABLE tournaments (
    tournament_id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL CHECK (status IN ('LOBBY', 'RUNNING', 'FINISHED', 'CANCELLED')),
    player_ids UUID[] NOT NULL,
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX tournaments_player_ids_idx ON tournaments USING GIN (player_ids);
CREATE INDEX tournaments_status_updated_idx ON tournaments (status, updated_at DESC);
