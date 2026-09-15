package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class RoomMatchLocalizationTest {
    @Test
    fun roomAndPlayerLabelsDoNotFallBackToEnglish() {
        assertLocalized(
            TextKey.GameRooms, TextKey.Ready, TextKey.YouAreHost,
            TextKey.TeammateNumber, TextKey.OpponentNumber,
            TextKey.LocalPlayerLabel, TextKey.OpponentLabel,
            TextKey.GameDefaultRoom, TextKey.YourTeam, TextKey.OpponentTeam,
        )
    }

    @Test
    fun liveMatchMetricsDoNotFallBackToEnglish() {
        assertLocalized(
            TextKey.PaceProgress, TextKey.SpeedValue,
            TextKey.CorrectWrongSummary, TextKey.Measuring,
            TextKey.Fastest, TextKey.Slowest, TextKey.TurnRange,
        )
    }

    @Test
    fun matchAndInviteStatusesDoNotFallBackToEnglish() {
        assertLocalized(
            TextKey.DrawResult, TextKey.FinalMatch,
            TextKey.OpponentLeft, TextKey.WaitingForResponse,
            TextKey.Invited, TextKey.SendingShort,
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
