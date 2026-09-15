package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class RankWalletSummaryLocalizationTest {
    @Test
    fun rankModeSummaryAndWalletSourceCopyDoesNotFallBackToEnglish() {
        val keys = listOf(
            TextKey.RankBronze, TextKey.RankSilver, TextKey.RankGold,
            TextKey.RankPlatinum, TextKey.RankDiamond,
            TextKey.RankMaster, TextKey.RankChallenger,
            TextKey.ModeWinRate, TextKey.ModeMatchSummary, TextKey.ScoreAverage,
            TextKey.ClanMissionSource, TextKey.CosmeticPurchaseSource,
            TextKey.TournamentEntrySource, TextKey.TournamentPrizeSource,
        )
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
