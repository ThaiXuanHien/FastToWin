package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class SeasonRewardSummaryLocalizationTest {
    @Test
    fun seasonSummaryAndRewardCopyDoesNotFallBackToEnglish() {
        assertLocalized(
            TextKey.SeasonsPlayed, TextKey.SeasonSummaryLine,
            TextKey.HighestTierReached, TextKey.SeasonPeakElo,
            TextKey.PassedTier, TextKey.TierStartsAtElo,
            TextKey.RewardReceiptDescription, TextKey.SeasonRewardAdded,
            TextKey.SeasonSummaryTitle, TextKey.SeasonSummaryCongratulations,
            TextKey.Great, TextKey.SeasonRewardAvatarName,
            TextKey.AddedToCollection, TextKey.ExclusiveSeasonFrame,
            TextKey.ExclusiveSeasonTitle, TextKey.ExclusiveSeasonCosmetic,
            TextKey.SeasonEnded, TextKey.DaysRemaining,
            TextKey.DaysHoursRemaining, TextKey.HoursMinutesRemaining,
            TextKey.MinutesRemaining,
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
