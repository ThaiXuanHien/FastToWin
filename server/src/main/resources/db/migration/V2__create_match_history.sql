CREATE TABLE matches (
    id UUID PRIMARY KEY,
    room_name VARCHAR(48) NOT NULL,
    game_mode VARCHAR(16) NOT NULL CHECK (game_mode IN ('ORDER', 'TIME_ATTACK')),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL,
    winner_user_id UUID REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE match_players (
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(32) NOT NULL,
    score INTEGER NOT NULL CHECK (score >= 0),
    outcome VARCHAR(8) NOT NULL CHECK (outcome IN ('WIN', 'LOSS', 'DRAW')),
    PRIMARY KEY (match_id, user_id)
);

CREATE INDEX match_players_user_history_idx ON match_players(user_id, match_id);

CREATE TABLE player_stats (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    total_matches INTEGER NOT NULL DEFAULT 0 CHECK (total_matches >= 0),
    wins INTEGER NOT NULL DEFAULT 0 CHECK (wins >= 0),
    losses INTEGER NOT NULL DEFAULT 0 CHECK (losses >= 0),
    draws INTEGER NOT NULL DEFAULT 0 CHECK (draws >= 0),
    highest_score INTEGER NOT NULL DEFAULT 0 CHECK (highest_score >= 0),
    current_win_streak INTEGER NOT NULL DEFAULT 0 CHECK (current_win_streak >= 0),
    best_win_streak INTEGER NOT NULL DEFAULT 0 CHECK (best_win_streak >= 0),
    updated_at TIMESTAMPTZ NOT NULL
);
