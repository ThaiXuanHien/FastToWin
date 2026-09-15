package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class LeaderboardLocalizationTest {
    @Test
    fun leaderboardFiltersAndEmptyStatesDoNotFallBackToEnglish() {
        val keys = setOf(
            TextKey.FameRaceDescription, TextKey.PreviousSeason, TextKey.AllTime, TextKey.History,
            TextKey.TopPlayers, TextKey.WarriorsCount, TextKey.CurrentSeasonLeaderboardEmpty,
            TextKey.PreviousSeasonLeaderboardEmpty, TextKey.AllTimeLeaderboardEmpty,
            TextKey.YourPosition, TextKey.StrongestClans, TextKey.StrongestClansDescription,
            TextKey.TopClans, TextKey.ClanLeaderboardEmpty, TextKey.YourClan,
            TextKey.RankingAwaiting, TextKey.MembersCount, TextKey.TotalElo,
            TextKey.LeaderboardPlayerSummary,
        )
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts
        allLocalizationCatalogs.filterKeys { it != AppLanguage.ENGLISH }.forEach { (language, catalog) ->
            keys.forEach { key ->
                assertNotEquals(english.getValue(key), catalog.texts.getValue(key), "${language.code}/$key")
            }
        }
    }
}
