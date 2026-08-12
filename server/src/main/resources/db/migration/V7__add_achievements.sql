CREATE TABLE achievements (
    code VARCHAR(32) PRIMARY KEY,
    title VARCHAR(64) NOT NULL,
    description VARCHAR(160) NOT NULL,
    sort_order INTEGER NOT NULL UNIQUE
);

CREATE TABLE user_achievements (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_code VARCHAR(32) NOT NULL REFERENCES achievements(code) ON DELETE RESTRICT,
    unlocked_at TIMESTAMPTZ NOT NULL,
    match_id UUID REFERENCES matches(id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, achievement_code)
);

CREATE INDEX user_achievements_recent_idx
    ON user_achievements (user_id, unlocked_at DESC);

INSERT INTO achievements (code, title, description, sort_order) VALUES
    ('FIRST_WIN', 'Khởi đầu chiến thắng', 'Giành chiến thắng đầu tiên.', 10),
    ('WIN_10', 'Người chiến thắng', 'Giành tổng cộng 10 chiến thắng.', 20),
    ('STREAK_5', 'Không thể cản phá', 'Đạt chuỗi thắng 5 trận liên tiếp.', 30),
    ('PERFECT_GAME', 'Đôi mắt tinh anh', 'Hoàn thành một trận có lượt đúng mà không bấm sai.', 40),
    ('SPEED_50', 'Tia chớp 50', 'Tự chọn đúng đủ 50 số trong tối đa 30 giây.', 50);
