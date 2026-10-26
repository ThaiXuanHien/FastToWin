package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.economyProgressionTexts

internal val vietnameseCatalog = LocalizationCatalog(
    language = AppLanguage.VIETNAMESE,
    texts = mapOf(
        TextKey.ClanMemberProfileTitle to "Thành viên bang",
        TextKey.ViewClanMemberProfile to "Xem hồ sơ của {player}",
        TextKey.ClanMemberRole to "Vai trò trong bang",
        TextKey.ClanQuestContribution to "Đóng góp nhiệm vụ",
        TextKey.ClanDonatedGold to "Vàng đã quyên góp",
        TextKey.ClanDonatedGems to "Kim cương đã quyên góp",
        TextKey.SettingsTitle to "Cài đặt",
        TextKey.LanguageTitle to "Ngôn ngữ",
        TextKey.ChooseLanguageTitle to "Chọn ngôn ngữ",
        TextKey.SystemLanguage to "Theo hệ thống",
        TextKey.ResolvedSystemLanguage to "Theo hệ thống · {language}",
        TextKey.AppearanceTitle to "Giao diện",
        TextKey.AppearanceSubtitle to "Tùy chọn được lưu riêng trên thiết bị này.",
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
) + vietnameseShellAuthTexts + vietnameseGameplayTexts + vietnameseProfileTexts + vietnameseSocialShopTexts + vietnameseProtocolMessageTexts + vietnameseDeliveryTexts + residualTexts(AppLanguage.VIETNAMESE) + economyProgressionTexts.getValue(AppLanguage.VIETNAMESE) + playQuotaTexts.getValue(AppLanguage.VIETNAMESE),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.OTHER to "{count} người chơi"
        )
    ) + vietnameseProfileQuantities + vietnameseSocialShopQuantities
)
