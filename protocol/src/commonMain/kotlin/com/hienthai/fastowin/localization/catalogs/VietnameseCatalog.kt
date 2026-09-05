package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val vietnameseCatalog = LocalizationCatalog(
    language = AppLanguage.VIETNAMESE,
    texts = mapOf(
        TextKey.Back to "Quay lại",
        TextKey.Cancel to "Hủy",
        TextKey.Confirm to "Xác nhận",
        TextKey.Retry to "Thử lại",
        TextKey.Close to "Đóng",
        TextKey.Save to "Lưu",
        TextKey.Delete to "Xóa",
        TextKey.Loading to "Đang tải…",
        TextKey.UnknownError to "Đã có lỗi xảy ra. Vui lòng thử lại.",
        TextKey.WelcomePlayer to "Chào {player}!"
    ),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.OTHER to "{count} người chơi"
        )
    )
)
