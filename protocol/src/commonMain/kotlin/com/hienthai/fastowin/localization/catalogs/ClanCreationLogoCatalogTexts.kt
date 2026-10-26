package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

internal val clanCreationCostTexts: Map<AppLanguage, Map<TextKey, String>> =
    AppLanguage.entries.associateWith { language ->
        mapOf(
            TextKey.ServerClanCreationInsufficientFunds to when (language) {
                AppLanguage.VIETNAMESE -> "Cần 10.000 Vàng và 100 Gem để tạo bang."
                else -> "You need 10,000 Gold and 100 Gems to create a clan."
            },
            TextKey.ServerClanCreationLevelTooLow to when (language) {
                AppLanguage.VIETNAMESE -> "Cần đạt cấp 10 để tạo bang."
                else -> "Reach level 10 to create a clan."
            },
            TextKey.ClanCreationLevelRequirement to when (language) {
                AppLanguage.VIETNAMESE -> "Yêu cầu cấp {level} • Hiện tại: cấp {current}"
                else -> "Required level: {level} • Current level: {current}"
            }
        )
    }
