INSERT INTO achievements (code, title, description, sort_order)
VALUES (
    'DAILY_STREAK_7',
    'Khởi đầu đều đặn',
    'Điểm danh 7 ngày liên tiếp.',
    60
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO user_achievements (user_id, achievement_code, unlocked_at, match_id)
SELECT ps.user_id,
       'DAILY_STREAK_7',
       COALESCE(
           (
               SELECT MIN(dci.created_at)
               FROM daily_check_ins dci
               WHERE dci.user_id = ps.user_id
                 AND dci.streak_after >= 7
           ),
           CURRENT_TIMESTAMP
       ),
       NULL
FROM player_stats ps
WHERE ps.best_daily_check_in_streak >= 7
ON CONFLICT (user_id, achievement_code) DO NOTHING;
