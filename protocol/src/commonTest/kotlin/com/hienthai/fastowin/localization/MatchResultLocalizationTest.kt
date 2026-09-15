package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class MatchResultLocalizationTest {
    @Test
    fun matchResultAndPaceCopyDoesNotFallBackToEnglish() {
        val keys = setOf(
            TextKey.DrawResultDescription, TextKey.VictoryResultDescription,
            TextKey.OpposingTeamVictoryDescription, TextKey.OpponentVictoryDescription,
            TextKey.MatchResultTitle, TextKey.ShareResult, TextKey.ShareResultError,
            TextKey.PlayerYou, TextKey.ResultLabel, TextKey.PlacementProgress,
            TextKey.TournamentFinishedDescription, TextKey.BracketUpdatedDescription,
            TextKey.ViewBracket, TextKey.YourSummary, TextKey.AverageReaction,
            TextKey.Accuracy, TextKey.CorrectWrongDuration, TextKey.PaceAnalysis,
            TextKey.InsufficientPaceAnalysis, TextKey.PaceSegmentHint,
        )
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts
        allLocalizationCatalogs.filterKeys { it != AppLanguage.ENGLISH }.forEach { (language, catalog) ->
            keys.forEach { key ->
                assertNotEquals(english.getValue(key), catalog.texts.getValue(key), "${language.code}/$key")
            }
        }
    }
}
