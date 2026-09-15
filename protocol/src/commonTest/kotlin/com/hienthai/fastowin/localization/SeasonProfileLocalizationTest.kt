package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class SeasonProfileLocalizationTest {
    @Test
    fun seasonProgressAndRewardCopyDoesNotFallBackToEnglish() {
        val keys = setOf(
            TextKey.SeasonHistoryDescription, TextKey.LoadingSeasonHistory,
            TextKey.SeasonHistoryRefreshHint, TextKey.NoSeasonHistory,
            TextKey.NoSeasonHistoryDescription, TextKey.HighestTier, TextKey.HighestElo,
            TextKey.FinalElo, TextKey.FinalRank, TextKey.NotRanked,
            TextKey.SeasonRewardReceived, TextKey.SeasonRewardProcessing,
            TextKey.NoPlacementReward, TextKey.SeasonProgressPlacement,
            TextKey.EloToNextTier, TextKey.HideTierRewards, TextKey.ViewTierRewards,
            TextKey.FinishPlacementHint, TextKey.HeldReward, TextKey.ReachedHighestTier,
        )
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts
        allLocalizationCatalogs.filterKeys { it != AppLanguage.ENGLISH }.forEach { (language, catalog) ->
            keys.forEach { key ->
                assertNotEquals(english.getValue(key), catalog.texts.getValue(key), "${language.code}/$key")
            }
        }
    }
}
