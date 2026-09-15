package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class CollectionMatchDetailsLocalizationTest {
    @Test
    fun matchDetailsAndHistoryNavigationDoNotFallBackToEnglish() {
        assertLocalized(
            TextKey.LoadingMatch, TextKey.MatchDetails, TextKey.ReactionLabel,
            TextKey.NoEloChange, TextKey.MatchTapSummary,
            TextKey.Previous, TextKey.Next,
        )
    }

    @Test
    fun collectionUnlockRequirementsDoNotFallBackToEnglish() {
        assertLocalized(
            TextKey.LockedCosmetic, TextKey.UnlockLevel,
            TextKey.UnlockPerfectFrame, TextKey.UnlockPersistentFrame,
            TextKey.UnlockChampionTitle, TextKey.UnlockSpeedTitle,
            TextKey.UnlockDiligentTitle, TextKey.UnlockCheckInAvatar,
        )
    }

    @Test
    fun foundationalAchievementsDoNotFallBackToEnglish() {
        assertLocalized(
            TextKey.AchievementWinTenTitle, TextKey.AchievementWinTenDescription,
            TextKey.AchievementPerfectTitle, TextKey.AchievementPerfectDescription,
            TextKey.AchievementSpeedTitle, TextKey.AchievementSpeedDescription,
            TextKey.AchievementCheckInTitle, TextKey.AchievementCheckInDescription,
        )
    }

    private fun assertLocalized(vararg keys: TextKey) {
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts
        allLocalizationCatalogs.filterKeys {
            it != AppLanguage.ENGLISH && it != AppLanguage.VIETNAMESE
        }.forEach { (language, catalog) ->
            keys.forEach { key ->
                assertNotEquals(english.getValue(key), catalog.texts.getValue(key), "${language.code}/$key")
            }
        }
    }
}
