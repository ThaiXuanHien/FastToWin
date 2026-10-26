package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

internal val maintenanceTexts = mapOf(
    AppLanguage.ENGLISH to mapOf(
    TextKey.MaintenanceDetails to "Details",
    TextKey.MaintenanceAnnouncing to "Maintenance ahead",
    TextKey.MaintenanceDraining to "Finishing current matches",
    TextKey.MaintenancePrepare to "Please finish your match before maintenance.",
    TextKey.MaintenanceDrainGuidance to "Current matches can finish. New matches and purchases are paused.",
    TextKey.MaintenanceCountdown to "Next maintenance stage in {seconds}s (estimated)",
    TextKey.MaintenanceCountdownCompact to "About {seconds}s",
    TextKey.MaintenanceExpectedEnd to "Expected end: {time} · Vietnam time (UTC+7). This estimate does not reopen the game.",
    TextKey.MaintenanceRecheckUnavailable to "Unable to recheck the server. The last maintenance status is retained; retrying automatically.",
    ),
    AppLanguage.VIETNAMESE to mapOf(
    TextKey.MaintenanceDetails to "Chi tiết",
    TextKey.MaintenanceAnnouncing to "Sắp bảo trì",
    TextKey.MaintenanceDraining to "Đang chờ trận kết thúc",
    TextKey.MaintenancePrepare to "Hãy hoàn tất trận đấu trước thời gian bảo trì.",
    TextKey.MaintenanceDrainGuidance to "Trận đang diễn ra có thể hoàn tất. Tạm dừng trận mới và mua hàng.",
    TextKey.MaintenanceCountdown to "Giai đoạn tiếp theo sau {seconds} giây (dự kiến)",
    TextKey.MaintenanceCountdownCompact to "Khoảng {seconds} giây",
    TextKey.MaintenanceExpectedEnd to "Kết thúc dự kiến: {time} · Giờ Việt Nam (UTC+7). Mốc này không tự mở lại trò chơi.",
    TextKey.MaintenanceRecheckUnavailable to "Chưa kiểm tra lại được máy chủ. Giữ trạng thái bảo trì gần nhất và tự động thử lại.",
    )
)
