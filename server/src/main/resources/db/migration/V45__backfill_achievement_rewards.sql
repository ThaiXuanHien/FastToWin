CREATE TEMP TABLE achievement_backfill_candidates (
    user_id UUID NOT NULL,
    achievement_code VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, achievement_code)
) ON COMMIT DROP;

-- Preserve active unlocks created before achievement rewards had wallet receipts.
INSERT INTO achievement_backfill_candidates (user_id, achievement_code)
SELECT ua.user_id, ua.achievement_code
FROM user_achievements ua
JOIN achievements a ON a.code = ua.achievement_code
WHERE a.is_active = TRUE
ON CONFLICT DO NOTHING;

-- Statistics-backed achievements.
INSERT INTO achievement_backfill_candidates (user_id, achievement_code)
SELECT ps.user_id, candidate.code
FROM player_stats ps
CROSS JOIN LATERAL (
    VALUES
        ('FIRST_WIN', ps.wins >= 1),
        ('WINS_10', ps.wins >= 10),
        ('WINS_50', ps.wins >= 50),
        ('WIN_STREAK_10', ps.best_win_streak >= 10),
        ('CHECKIN_STREAK_7', ps.best_daily_check_in_streak >= 7),
        ('CHECKIN_STREAK_30', ps.best_daily_check_in_streak >= 30),
        ('CHECKINS_50', ps.total_daily_check_ins >= 50),
        ('CHECKINS_100', ps.total_daily_check_ins >= 100)
) AS candidate(code, qualifies)
WHERE candidate.qualifies
ON CONFLICT DO NOTHING;

-- Ranked wins are derived from persisted match history rather than total wins.
INSERT INTO achievement_backfill_candidates (user_id, achievement_code)
SELECT mp.user_id, 'RANKED_WINS_100'
FROM match_players mp
JOIN matches m ON m.id = mp.match_id
WHERE mp.outcome = 'WIN' AND m.match_type = 'RANKED'
GROUP BY mp.user_id
HAVING COUNT(*) >= 100
ON CONFLICT DO NOTHING;

-- Perfect and accuracy achievements are counted per match.
WITH match_quality AS (
    SELECT mp.user_id,
           mp.match_id,
           mp.outcome,
           COUNT(e.id) FILTER (WHERE e.result = 'ACCEPTED') AS accepted,
           COUNT(e.id) FILTER (WHERE e.result = 'REJECTED') AS rejected,
           COUNT(e.id) AS selections
    FROM match_players mp
    LEFT JOIN match_events e ON e.match_id = mp.match_id AND e.user_id = mp.user_id
    GROUP BY mp.user_id, mp.match_id, mp.outcome
), quality_counts AS (
    SELECT user_id,
           COUNT(*) FILTER (
               WHERE outcome = 'WIN' AND accepted > 0 AND rejected = 0
           ) AS perfect_matches,
           COUNT(*) FILTER (
               WHERE accepted > 0 AND accepted * 100 >= selections * 90
           ) AS accurate_matches
    FROM match_quality
    GROUP BY user_id
)
INSERT INTO achievement_backfill_candidates (user_id, achievement_code)
SELECT user_id, code
FROM quality_counts
CROSS JOIN LATERAL (
    VALUES
        ('PERFECT_MATCH_1', perfect_matches >= 1),
        ('PERFECT_MATCHES_10', perfect_matches >= 10),
        ('ACCURACY_90_TEN', accurate_matches >= 10)
) AS candidate(code, qualifies)
WHERE candidate.qualifies
ON CONFLICT DO NOTHING;

-- Reaction time follows the same shared-target timing used by match statistics.
WITH accepted_events AS (
    SELECT e.user_id,
           e.match_id,
           GREATEST(
               EXTRACT(EPOCH FROM (
                   e.occurred_at - LAG(e.occurred_at, 1, m.started_at)
                       OVER (PARTITION BY e.match_id ORDER BY e.sequence)
               )) * 1000,
               0
           ) AS reaction_ms
    FROM match_events e
    JOIN matches m ON m.id = e.match_id
    WHERE e.result = 'ACCEPTED'
), response_counts AS (
    SELECT user_id,
           COUNT(*) FILTER (WHERE average_ms < 2500) AS response_2500,
           COUNT(*) FILTER (WHERE average_ms < 1500) AS response_1500
    FROM (
        SELECT user_id, match_id, AVG(reaction_ms) AS average_ms
        FROM accepted_events
        GROUP BY user_id, match_id
    ) per_match
    GROUP BY user_id
)
INSERT INTO achievement_backfill_candidates (user_id, achievement_code)
SELECT user_id, code
FROM response_counts
CROSS JOIN LATERAL (
    VALUES
        ('RESPONSE_2500_TEN', response_2500 >= 10),
        ('RESPONSE_1500_TEN', response_1500 >= 10)
) AS candidate(code, qualifies)
WHERE candidate.qualifies
ON CONFLICT DO NOTHING;

-- Include XP about to be granted so backfill rewards can also reach player level 30.
INSERT INTO achievement_backfill_candidates (user_id, achievement_code)
SELECT ps.user_id, 'PLAYER_LEVEL_30'
FROM player_stats ps
LEFT JOIN (
    SELECT c.user_id, SUM(a.reward_xp) AS pending_xp
    FROM achievement_backfill_candidates c
    JOIN achievements a ON a.code = c.achievement_code
    LEFT JOIN wallet_transactions wt
      ON wt.user_id = c.user_id
     AND wt.source_type = 'ACHIEVEMENT'
     AND wt.source_id = c.achievement_code
    WHERE wt.id IS NULL
    GROUP BY c.user_id
) pending ON pending.user_id = ps.user_id
WHERE (ps.experience_points + COALESCE(pending.pending_xp, 0)) / 100 + 1 >= 30
ON CONFLICT DO NOTHING;

INSERT INTO user_achievements (user_id, achievement_code, unlocked_at, match_id)
SELECT user_id, achievement_code, CURRENT_TIMESTAMP, NULL
FROM achievement_backfill_candidates
ON CONFLICT (user_id, achievement_code) DO NOTHING;

CREATE TEMP TABLE achievement_backfill_receipts (
    user_id UUID NOT NULL,
    gold_delta INTEGER NOT NULL,
    gems_delta INTEGER NOT NULL,
    xp_delta INTEGER NOT NULL
) ON COMMIT DROP;

WITH inserted AS (
    INSERT INTO wallet_transactions (
        id, user_id, source_type, source_id, gold_delta, gems_delta, xp_delta, created_at
    )
    SELECT gen_random_uuid(), c.user_id, 'ACHIEVEMENT', c.achievement_code,
           a.reward_gold, a.reward_gems, a.reward_xp, CURRENT_TIMESTAMP
    FROM achievement_backfill_candidates c
    JOIN achievements a ON a.code = c.achievement_code
    ON CONFLICT (user_id, source_type, source_id) DO NOTHING
    RETURNING user_id, gold_delta, gems_delta, xp_delta
)
INSERT INTO achievement_backfill_receipts (user_id, gold_delta, gems_delta, xp_delta)
SELECT user_id, gold_delta, gems_delta, xp_delta
FROM inserted;

UPDATE player_stats ps
SET gold = ps.gold + rewards.gold,
    gems = ps.gems + rewards.gems,
    experience_points = ps.experience_points + rewards.xp,
    updated_at = CURRENT_TIMESTAMP
FROM (
    SELECT user_id, SUM(gold_delta) AS gold, SUM(gems_delta) AS gems, SUM(xp_delta) AS xp
    FROM achievement_backfill_receipts
    GROUP BY user_id
) rewards
WHERE ps.user_id = rewards.user_id;

INSERT INTO player_cosmetics (user_id, cosmetic_id, cosmetic_type, acquired_at)
SELECT c.user_id, a.frame_id, 'FRAME', CURRENT_TIMESTAMP
FROM achievement_backfill_candidates c
JOIN achievements a ON a.code = c.achievement_code
WHERE a.frame_id IS NOT NULL
ON CONFLICT (user_id, cosmetic_id) DO NOTHING;

INSERT INTO player_cosmetics (user_id, cosmetic_id, cosmetic_type, acquired_at)
SELECT c.user_id, a.title_id, 'TITLE', CURRENT_TIMESTAMP
FROM achievement_backfill_candidates c
JOIN achievements a ON a.code = c.achievement_code
WHERE a.title_id IS NOT NULL
ON CONFLICT (user_id, cosmetic_id) DO NOTHING;
