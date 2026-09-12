ALTER TABLE player_stats
    ADD COLUMN lifetime_earned_gold BIGINT NOT NULL DEFAULT 0 CHECK (lifetime_earned_gold >= 0),
    ADD COLUMN lifetime_earned_gems BIGINT NOT NULL DEFAULT 0 CHECK (lifetime_earned_gems >= 0),
    ADD COLUMN lifetime_earned_gold_reached_at TIMESTAMPTZ,
    ADD COLUMN lifetime_earned_gems_reached_at TIMESTAMPTZ;

WITH eligible_income AS (
    SELECT user_id,
           COALESCE(SUM(GREATEST(gold_delta, 0)), 0) AS earned_gold,
           COALESCE(SUM(GREATEST(gems_delta, 0)), 0) AS earned_gems,
           MAX(created_at) FILTER (WHERE gold_delta > 0) AS gold_reached_at,
           MAX(created_at) FILTER (WHERE gems_delta > 0) AS gems_reached_at
    FROM wallet_transactions
    WHERE source_type IN (
        'MATCH', 'DAILY_CHECK_IN', 'MISSION', 'ACHIEVEMENT',
        'SEASON_REWARD', 'CLAN_QUEST', 'TOURNAMENT_PRIZE'
    )
    GROUP BY user_id
)
UPDATE player_stats stats
SET lifetime_earned_gold = eligible_income.earned_gold,
    lifetime_earned_gems = eligible_income.earned_gems,
    lifetime_earned_gold_reached_at = eligible_income.gold_reached_at,
    lifetime_earned_gems_reached_at = eligible_income.gems_reached_at
FROM eligible_income
WHERE stats.user_id = eligible_income.user_id;

CREATE OR REPLACE FUNCTION update_lifetime_earned_assets()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.source_type IN (
        'MATCH', 'DAILY_CHECK_IN', 'MISSION', 'ACHIEVEMENT',
        'SEASON_REWARD', 'CLAN_QUEST', 'TOURNAMENT_PRIZE'
    ) THEN
        UPDATE player_stats
        SET lifetime_earned_gold = lifetime_earned_gold + GREATEST(NEW.gold_delta, 0),
            lifetime_earned_gems = lifetime_earned_gems + GREATEST(NEW.gems_delta, 0),
            lifetime_earned_gold_reached_at = CASE
                WHEN NEW.gold_delta > 0 THEN NEW.created_at
                ELSE lifetime_earned_gold_reached_at
            END,
            lifetime_earned_gems_reached_at = CASE
                WHEN NEW.gems_delta > 0 THEN NEW.created_at
                ELSE lifetime_earned_gems_reached_at
            END
        WHERE user_id = NEW.user_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER wallet_transaction_lifetime_earned_assets
AFTER INSERT ON wallet_transactions
FOR EACH ROW EXECUTE FUNCTION update_lifetime_earned_assets();

CREATE INDEX player_stats_earned_gold_leaderboard_idx
    ON player_stats (
        lifetime_earned_gold DESC,
        lifetime_earned_gold_reached_at ASC,
        user_id ASC
    );

CREATE INDEX player_stats_earned_gems_leaderboard_idx
    ON player_stats (
        lifetime_earned_gems DESC,
        lifetime_earned_gems_reached_at ASC,
        user_id ASC
    );
