package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class MatchFlowLocalizationTest {
    @Test
    fun matchAndResultActionsDoNotFallBackToEnglish() {
        val keys = setOf(
            TextKey.ReactionHappy, TextKey.ReactionLaugh, TextKey.ReactionFire,
            TextKey.ReactionVictory, TextKey.ReactionLove, TextKey.ReactionSpeed,
            TextKey.ExitMatchTitle, TextKey.ForfeitCasualDescription,
            TextKey.ForfeitRankedDescription, TextKey.ExitMatch, TextKey.ContinuePlaying,
            TextKey.GameDisconnected, TextKey.GameReconnecting, TextKey.TieScoreWarning,
            TextKey.CloseScoreWarning, TextKey.LivesCount, TextKey.SendReaction,
            TextKey.NextNumber, TextKey.RematchInviteTitle, TextKey.RematchInviteDescription,
            TextKey.VictoryResult, TextKey.DefeatResult, TextKey.ForfeitResultDescription,
            TextKey.ReturnToLobby,
        )
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts
        allLocalizationCatalogs.filterKeys { it != AppLanguage.ENGLISH }.forEach { (language, catalog) ->
            keys.forEach { key ->
                assertNotEquals(english.getValue(key), catalog.texts.getValue(key), "${language.code}/$key")
            }
        }
    }
}
