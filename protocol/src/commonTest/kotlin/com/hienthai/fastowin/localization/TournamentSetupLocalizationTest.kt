package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TournamentSetupLocalizationTest {
    @Test
    fun tournamentCreatedNoticeIsNeutralForPublicAndPrivateTournaments() {
        assertEquals("Created a 4-player tournament.", LocalizationService(AppLanguage.ENGLISH)
            .text(TextKey.TournamentCreatedNotice, mapOf("players" to 4)))
        assertEquals("Đã tạo giải 4 người.", LocalizationService(AppLanguage.VIETNAMESE)
            .text(TextKey.TournamentCreatedNotice, mapOf("players" to 4)))
    }

    @Test
    fun tournamentSetupAndLobbyCopyDoesNotFallBackToEnglish() {
        assertLocalized(
            TextKey.TournamentLobby, TextKey.ChampionName,
            TextKey.TournamentLobbySummary, TextKey.BracketInProgress,
            TextKey.TournamentPrizeAwarded, TextKey.CreateAnotherTournament,
            TextKey.DecideLater, TextKey.ChooseTournamentMode,
            TextKey.CreatePrivateTournament, TextKey.TournamentSize,
            TextKey.TournamentFourFormat, TextKey.TournamentEightFormat,
            TextKey.TournamentSixteenFormat, TextKey.TournamentName,
            TextKey.TournamentNameExample, TextKey.GameModeLabel,
            TextKey.EntryFeeGold, TextKey.Free, TextKey.Custom,
            TextKey.EnterEntryFee, TextKey.EntryFeeExample,
            TextKey.TournamentNoElo, TextKey.PlayerCountUpper,
            TextKey.EntryFee, TextKey.Prize, TextKey.GoldAmount,
            TextKey.Participants, TextKey.WaitingForPlayerEllipsis,
            TextKey.TournamentHost,
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
