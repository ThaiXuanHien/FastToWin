package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class ProfileStatsLocalizationTest {
    @Test
    fun playerStatisticsAndAchievementEmptyStateDoNotFallBackToEnglish() {
        assertLocalized(
            TextKey.WinRate, TextKey.AccuracyLabel, TextKey.RecentFormTen,
            TextKey.EloChange, TextKey.Overview,
            TextKey.Losses, TextKey.DrawsLabel, TextKey.HighScore,
            TextKey.CorrectWrongLabel, TextKey.ModeStatistics,
            TextKey.AchievementsTitle, TextKey.NoAchievements,
            TextKey.Frames, TextKey.CurrentSessionDuration,
        )
    }

    @Test
    fun profileMissionNamesAndWeekdaysDoNotFallBackToEnglish() {
        assertLocalized(
            TextKey.MissionCorrectHundred, TextKey.MissionPerfectWin,
            TextKey.WeekMonday, TextKey.WeekTuesday, TextKey.WeekWednesday,
            TextKey.WeekThursday, TextKey.WeekFriday,
            TextKey.WeekSaturday, TextKey.WeekSunday,
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
