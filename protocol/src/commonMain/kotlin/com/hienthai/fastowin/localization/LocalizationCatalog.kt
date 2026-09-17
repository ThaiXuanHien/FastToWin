package com.hienthai.fastowin.localization

import com.hienthai.fastowin.localization.catalogs.accountSecurityTexts
import com.hienthai.fastowin.localization.catalogs.brazilianPortugueseCatalog
import com.hienthai.fastowin.localization.catalogs.checkInTexts
import com.hienthai.fastowin.localization.catalogs.challengeSharingTexts
import com.hienthai.fastowin.localization.catalogs.clanCreationLogoTexts
import com.hienthai.fastowin.localization.catalogs.clanCreationCostTexts
import com.hienthai.fastowin.localization.catalogs.clanNotificationTexts
import com.hienthai.fastowin.localization.catalogs.collectionMatchDetailsTexts
import com.hienthai.fastowin.localization.catalogs.englishCatalog
import com.hienthai.fastowin.localization.catalogs.expandedProfileTexts
import com.hienthai.fastowin.localization.catalogs.frenchCatalog
import com.hienthai.fastowin.localization.catalogs.friendManagementTexts
import com.hienthai.fastowin.localization.catalogs.framePresenceTexts
import com.hienthai.fastowin.localization.catalogs.germanCatalog
import com.hienthai.fastowin.localization.catalogs.indonesianCatalog
import com.hienthai.fastowin.localization.catalogs.japaneseCatalog
import com.hienthai.fastowin.localization.catalogs.koreanCatalog
import com.hienthai.fastowin.localization.catalogs.leaderboardTexts
import com.hienthai.fastowin.localization.catalogs.matchFlowTexts
import com.hienthai.fastowin.localization.catalogs.matchResultTexts
import com.hienthai.fastowin.localization.catalogs.matchSocialSharingTexts
import com.hienthai.fastowin.localization.catalogs.missionWalletTexts
import com.hienthai.fastowin.localization.catalogs.practiceTexts
import com.hienthai.fastowin.localization.catalogs.profileStatsTexts
import com.hienthai.fastowin.localization.catalogs.profileCollectionStatusTexts
import com.hienthai.fastowin.localization.catalogs.rankWalletSummaryTexts
import com.hienthai.fastowin.localization.catalogs.russianCatalog
import com.hienthai.fastowin.localization.catalogs.roomMatchTexts
import com.hienthai.fastowin.localization.catalogs.seasonProfileTexts
import com.hienthai.fastowin.localization.catalogs.seasonRewardSummaryTexts
import com.hienthai.fastowin.localization.catalogs.shopPurchaseTexts
import com.hienthai.fastowin.localization.catalogs.simplifiedChineseCatalog
import com.hienthai.fastowin.localization.catalogs.spanishCatalog
import com.hienthai.fastowin.localization.catalogs.thaiCatalog
import com.hienthai.fastowin.localization.catalogs.tournamentBracketTexts
import com.hienthai.fastowin.localization.catalogs.tournamentSetupTexts
import com.hienthai.fastowin.localization.catalogs.vietnameseCatalog

data class LocalizationCatalog(
    val language: AppLanguage,
    val texts: Map<TextKey, String>,
    val quantities: Map<QuantityKey, Map<PluralCategory, String>>
)

val allLocalizationCatalogs: Map<AppLanguage, LocalizationCatalog> = run {
    val baseCatalogs = listOf(
        englishCatalog, vietnameseCatalog, simplifiedChineseCatalog, japaneseCatalog,
        koreanCatalog, spanishCatalog, brazilianPortugueseCatalog, frenchCatalog,
        germanCatalog, indonesianCatalog, thaiCatalog, russianCatalog
    ).associateBy { it.language }
    val englishTexts = baseCatalogs.getValue(AppLanguage.ENGLISH).texts
    baseCatalogs.mapValues { (language, catalog) ->
        val additions = expandedProfileTexts[language].orEmpty() + leaderboardTexts[language].orEmpty() +
            matchFlowTexts[language].orEmpty() + seasonProfileTexts[language].orEmpty() +
            shopPurchaseTexts[language].orEmpty() + accountSecurityTexts[language].orEmpty() +
            matchResultTexts[language].orEmpty() + clanNotificationTexts[language].orEmpty() +
            practiceTexts[language].orEmpty() + checkInTexts[language].orEmpty() +
            missionWalletTexts[language].orEmpty() + roomMatchTexts[language].orEmpty() +
            profileStatsTexts[language].orEmpty() + collectionMatchDetailsTexts[language].orEmpty() +
            friendManagementTexts[language].orEmpty() + tournamentSetupTexts[language].orEmpty() +
            tournamentBracketTexts[language].orEmpty() + profileCollectionStatusTexts[language].orEmpty() +
            seasonRewardSummaryTexts[language].orEmpty() + framePresenceTexts[language].orEmpty() +
            rankWalletSummaryTexts[language].orEmpty() + clanCreationLogoTexts[language].orEmpty() +
            clanCreationCostTexts[language].orEmpty() +
            challengeSharingTexts[language].orEmpty() + matchSocialSharingTexts[language].orEmpty()
        val untranslated = additions.filter { (key, _) -> catalog.texts[key] == englishTexts[key] }
        catalog.copy(texts = catalog.texts + untranslated)
    }
}
