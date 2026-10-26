package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey

internal val clanCreationCostTexts: Map<AppLanguage, Map<TextKey, String>> =
    AppLanguage.entries.associateWith { language ->
        mapOf(
            TextKey.ServerClanCreationInsufficientFunds to when (language) {
                AppLanguage.VIETNAMESE -> "Cần 2.000 Vàng và 20 Gem để tạo bang."
                else -> "You need 2,000 Gold and 20 Gems to create a clan."
            }
        )
    }
