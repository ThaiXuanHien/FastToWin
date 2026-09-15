package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class CheckInLocalizationTest {
    @Test
    fun checkInCalendarAndMilestonesDoNotFallBackToEnglish() {
        val keys = setOf(
            TextKey.CheckIn, TextKey.StreakDays, TextKey.CheckInCalendarDescription,
            TextKey.PreviousMonth, TextKey.NextMonth, TextKey.MonthYear,
            TextKey.CheckedIn, TextKey.CheckInMilestones, TextKey.CheckInSummary,
            TextKey.ConsecutiveDays, TextKey.TotalCheckIns,
            TextKey.SteadyStartAchievement, TextKey.DiligentTitle,
            TextKey.PersistentFrame,
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
