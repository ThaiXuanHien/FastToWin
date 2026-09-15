package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class TournamentBracketLocalizationTest {
    @Test
    fun tournamentBracketAndActionsDoNotFallBackToEnglish() {
        val keys = listOf(
            TextKey.Bracket, TextKey.CancelTournament, TextKey.LeaveTournament,
            TextKey.AutomaticMatchesDescription, TextKey.Champion,
            TextKey.FinalRound, TextKey.SemifinalRound, TextKey.QuarterfinalRound,
            TextKey.RoundOfSixteen, TextKey.RoundNumber, TextKey.WaitingEllipsis,
            TextKey.ChampionCompact, TextKey.HideBracket,
            TextKey.JoinTournament, TextKey.InviteAction,
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
