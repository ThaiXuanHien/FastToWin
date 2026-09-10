ALTER TABLE achievements
    ADD COLUMN IF NOT EXISTS difficulty VARCHAR(16) NOT NULL DEFAULT 'EASY',
    ADD COLUMN IF NOT EXISTS target INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS reward_xp INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reward_gold INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reward_gems INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS frame_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS title_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE achievements
    DROP CONSTRAINT IF EXISTS achievements_difficulty_check,
    DROP CONSTRAINT IF EXISTS achievements_target_check,
    DROP CONSTRAINT IF EXISTS achievements_reward_xp_check,
    DROP CONSTRAINT IF EXISTS achievements_reward_gold_check,
    DROP CONSTRAINT IF EXISTS achievements_reward_gems_check;

ALTER TABLE achievements
    ADD CONSTRAINT achievements_difficulty_check
        CHECK (difficulty IN ('EASY', 'NORMAL', 'HARD', 'ELITE')),
    ADD CONSTRAINT achievements_target_check CHECK (target > 0),
    ADD CONSTRAINT achievements_reward_xp_check CHECK (reward_xp >= 0),
    ADD CONSTRAINT achievements_reward_gold_check CHECK (reward_gold >= 0),
    ADD CONSTRAINT achievements_reward_gems_check CHECK (reward_gems >= 0);

INSERT INTO achievements (
    code, title, description, sort_order, difficulty, target,
    reward_xp, reward_gold, reward_gems, frame_id, title_id, is_active
) VALUES
    ('FIRST_WIN', 'Khai Chiến', 'Thắng trận online đầu tiên.', 100, 'EASY', 1, 20, 100, 0, NULL, 'title_first_battle', TRUE),
    ('WINS_10', 'Cao Thủ', 'Thắng 10 trận online.', 110, 'NORMAL', 10, 50, 250, 0, 'frame_warrior', 'title_master', TRUE),
    ('WINS_50', 'Bách Chiến', 'Thắng 50 trận online.', 120, 'HARD', 50, 100, 500, 1, 'frame_veteran', 'title_veteran', TRUE),
    ('RANKED_WINS_100', 'Kẻ Chinh Phục', 'Thắng 100 trận đấu hạng.', 130, 'ELITE', 100, 200, 1000, 3, 'frame_diamond', 'title_conqueror', TRUE),
    ('WIN_STREAK_10', 'Bất Bại', 'Đạt chuỗi thắng 10 trận.', 140, 'ELITE', 10, 200, 1000, 3, 'frame_unyielding', 'title_undefeated', TRUE),
    ('PERFECT_MATCH_1', 'Nhất Kích', 'Thắng một trận không chọn sai.', 150, 'EASY', 1, 20, 100, 0, NULL, 'title_one_strike', TRUE),
    ('PERFECT_MATCHES_10', 'Quán Quân', 'Thắng 10 trận không chọn sai.', 160, 'HARD', 10, 100, 500, 1, 'frame_champion', NULL, TRUE),
    ('ACCURACY_90_TEN', 'Mắt Thần', 'Đạt ít nhất 90% chính xác trong 10 trận.', 170, 'NORMAL', 10, 50, 250, 0, NULL, 'title_divine_eye', TRUE),
    ('RESPONSE_2500_TEN', 'Phản Xạ Vàng', 'Phản ứng trung bình dưới 2,5 giây trong 10 trận.', 180, 'HARD', 10, 100, 500, 1, 'frame_speed_shadow', 'title_golden_reflex', TRUE),
    ('RESPONSE_1500_TEN', 'Thần Tốc', 'Phản ứng trung bình dưới 1,5 giây trong 10 trận.', 190, 'ELITE', 10, 200, 1000, 3, 'frame_lightning', 'title_godspeed', TRUE),
    ('CHECKIN_STREAK_7', 'Liệt Hỏa', 'Điểm danh liên tiếp 7 ngày.', 200, 'EASY', 7, 20, 100, 0, 'frame_wildfire', NULL, TRUE),
    ('CHECKIN_STREAK_30', 'Bất Diệt', 'Điểm danh liên tiếp 30 ngày.', 210, 'HARD', 30, 100, 500, 1, 'frame_immortal', NULL, TRUE),
    ('CHECKINS_50', 'Bền Bỉ', 'Điểm danh tổng cộng 50 lần.', 220, 'NORMAL', 50, 50, 250, 0, NULL, NULL, TRUE),
    ('CHECKINS_100', 'Chí Tôn', 'Điểm danh tổng cộng 100 lần.', 230, 'ELITE', 100, 200, 1000, 3, 'frame_supreme', NULL, TRUE),
    ('PLAYER_LEVEL_30', 'Chiến Thần', 'Đạt cấp người chơi 30.', 240, 'HARD', 30, 100, 500, 1, 'frame_legend', 'title_war_god', TRUE),
    ('CLAN_JOINED', 'Vinh Quang', 'Tham gia một bang.', 250, 'EASY', 1, 20, 100, 0, 'frame_glory', NULL, TRUE),
    ('CLAN_GOLD_10000', 'Long Uy', 'Quyên góp tổng cộng 10.000 Vàng.', 260, 'HARD', 10000, 100, 500, 1, 'frame_dragon_might', NULL, TRUE),
    ('CLAN_GEMS_50', 'Đế Vương', 'Quyên góp tổng cộng 50 Gem.', 270, 'ELITE', 50, 200, 1000, 3, 'frame_emperor', NULL, TRUE),
    ('CLAN_QUESTS_10', 'Trụ Cột', 'Nhận thưởng 10 nhiệm vụ bang.', 280, 'HARD', 10, 100, 500, 1, NULL, 'title_pillar', TRUE),
    ('CLAN_LEVEL_10', 'Vô Song', 'Thuộc bang khi bang đạt cấp 10.', 290, 'ELITE', 10, 200, 1000, 3, 'frame_peerless', NULL, TRUE)
ON CONFLICT (code) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    sort_order = EXCLUDED.sort_order,
    difficulty = EXCLUDED.difficulty,
    target = EXCLUDED.target,
    reward_xp = EXCLUDED.reward_xp,
    reward_gold = EXCLUDED.reward_gold,
    reward_gems = EXCLUDED.reward_gems,
    frame_id = EXCLUDED.frame_id,
    title_id = EXCLUDED.title_id,
    is_active = EXCLUDED.is_active;

UPDATE achievements
SET is_active = FALSE
WHERE code NOT IN (
    'FIRST_WIN', 'WINS_10', 'WINS_50', 'RANKED_WINS_100', 'WIN_STREAK_10',
    'PERFECT_MATCH_1', 'PERFECT_MATCHES_10', 'ACCURACY_90_TEN',
    'RESPONSE_2500_TEN', 'RESPONSE_1500_TEN', 'CHECKIN_STREAK_7',
    'CHECKIN_STREAK_30', 'CHECKINS_50', 'CHECKINS_100', 'PLAYER_LEVEL_30',
    'CLAN_JOINED', 'CLAN_GOLD_10000', 'CLAN_GEMS_50', 'CLAN_QUESTS_10',
    'CLAN_LEVEL_10'
);
