package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class ChallengeSharingLocalizationTest {
    @Test
    fun challengeSharingAndPracticeResultCopyDoesNotFallBackToEnglish() {
        val keys = listOf(
            TextKey.ChallengeCode, TextKey.ChallengeCodeHint,
            TextKey.PracticeNoElo, TextKey.ShareChallenge,
            TextKey.ReplaySameBoard, TextKey.CreateNewChallenge,
            TextKey.ReturnHome, TextKey.Reaction,
            TextKey.CorrectWrong, TextKey.AveragePerNumber,
            TextKey.HaveChallengeCode, TextKey.PlayChallenge,
            TextKey.InvalidChallengeCode, TextKey.ChallengeModeUnlock,
            TextKey.ChallengeShareText, TextKey.ShareChallengeSheetTitle,
            TextKey.RoomShareText,
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
