package com.hienthai.fastowin.navigation

enum class GameMode(
    val title: String,
    val description: String,
    val unlockLevel: Int
) {
    ORDER("Cổ điển", "Tìm các số từ 1 đến 50", 1),
    RANDOM_TARGET("Ngẫu nhiên", "Mục tiêu xuất hiện theo thứ tự bất ngờ", 3),
    TIME_BONUS("Thưởng thời gian", "Đúng +2 giây, sai -3 giây", 5),
    SPEED_UP("Tăng tốc", "Thời gian cho mỗi mục tiêu giảm dần", 7),
    SURVIVAL("Sinh tồn", "Hết 3 mạng khi bấm sai sẽ thua", 10),
    COMBO("Combo", "Chuỗi bấm đúng liên tục nhân điểm", 12),
    TIME_ATTACK("Đua 60 giây", "Chế độ cũ: ghi nhiều điểm trong 60 giây", 1),
    TEAM_2V2("Đồng đội 2v2", "Hai đội thi đấu cùng một bảng số", 5);

    val isLegacy: Boolean get() = this == TIME_ATTACK
}
