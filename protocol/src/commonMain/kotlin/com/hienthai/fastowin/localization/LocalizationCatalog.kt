package com.hienthai.fastowin.localization

import com.hienthai.fastowin.localization.catalogs.clanRaceTexts
import com.hienthai.fastowin.localization.catalogs.rewardedAdTexts
import com.hienthai.fastowin.localization.catalogs.ugcTexts
import com.hienthai.fastowin.localization.catalogs.announcementTexts
import com.hienthai.fastowin.localization.catalogs.rewardEventTexts
import com.hienthai.fastowin.localization.catalogs.clanCreationCostTexts
import com.hienthai.fastowin.localization.catalogs.englishCatalog
import com.hienthai.fastowin.localization.catalogs.maintenanceTexts
import com.hienthai.fastowin.localization.catalogs.giftMailboxTexts
import com.hienthai.fastowin.localization.catalogs.matchmakingGuardTexts
import com.hienthai.fastowin.localization.catalogs.playFlowConflictTexts
import com.hienthai.fastowin.localization.catalogs.tournamentScheduleTexts
import com.hienthai.fastowin.localization.catalogs.vietnameseCatalog

data class LocalizationCatalog(
    val language: AppLanguage,
    val texts: Map<TextKey, String>,
    val quantities: Map<QuantityKey, Map<PluralCategory, String>>
)

val allLocalizationCatalogs: Map<AppLanguage, LocalizationCatalog> = run {
    val baseCatalogs = listOf(englishCatalog, vietnameseCatalog).associateBy { it.language }
    val englishTexts = baseCatalogs.getValue(AppLanguage.ENGLISH).texts
    baseCatalogs.mapValues { (language, catalog) ->
        val additions = rewardedAdTexts.getValue(language) +
            missionActionTexts.getValue(language) + missionPlayerTexts.getValue(language) +
            clanCreationCostTexts.getValue(language) +
            matchmakingGuardTexts.getValue(language) + playFlowConflictTexts.getValue(language) +
            giftMailboxTexts.getValue(language) + maintenanceTexts.getValue(language) +
            rewardEventTexts.getValue(language) + announcementTexts.getValue(language) +
            tournamentScheduleTexts.getValue(language)
        val untranslated = additions.filter { (key, _) -> catalog.texts[key] == englishTexts[key] }
        catalog.copy(texts = catalog.texts + untranslated + clanRaceTexts.getValue(language) + legalTexts(language) + ugcTexts(language))
    }
}

/** Legal pages use the two supported languages. */
private fun legalTexts(language: AppLanguage): Map<TextKey, String> = if (language == AppLanguage.VIETNAMESE) {
    mapOf(
        TextKey.LegalTitle to "Thông tin dịch vụ",
        TextKey.LegalSubtitle to "Mở trang web công khai, không cần đăng nhập.",
        TextKey.LegalPrivacy to "Chính sách quyền riêng tư",
        TextKey.LegalTerms to "Điều khoản sử dụng",
        TextKey.LegalDeleteAccount to "Hướng dẫn xóa tài khoản",
    )
} else {
    mapOf(
        TextKey.LegalTitle to "Service information",
        TextKey.LegalSubtitle to "Open a public web page, no sign-in required.",
        TextKey.LegalPrivacy to "Privacy policy",
        TextKey.LegalTerms to "Terms of use",
        TextKey.LegalDeleteAccount to "Account deletion guide",
    )
}
