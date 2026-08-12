CREATE INDEX player_stats_leaderboard_idx
    ON player_stats (wins DESC, highest_score DESC, total_matches ASC, updated_at ASC);
