package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class PracticeLocalizationTest {
    @Test
    fun practiceInstructionsAndResultsDoNotFallBackToEnglish() {
        val keys = setOf(
            TextKey.PracticeHeader, TextKey.OfflineNoElo,
            TextKey.CorrectBoardCompleted, TextKey.CorrectNextTarget,
            TextKey.WrongNeedTarget, TextKey.Remaining, TextKey.Lives,
            TextKey.FoundAllNumbers, TextKey.SurvivalEnded,
            TextKey.SpeedUpEnded, TextKey.TimeBonusEnded,
            TextKey.TimeAttackEnded, TextKey.ChallengeEnded,
            TextKey.PracticeCompletionSummary, TextKey.PracticeHeroTitle,
            TextKey.PracticeHeroDescription, TextKey.PracticeNoServerNeeded,
            TextKey.StartNewPractice,
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
