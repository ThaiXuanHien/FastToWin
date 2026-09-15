package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class ProfileCoreLocalizationTest {
    @Test
    fun profileNavigationAndAccountCopyDoesNotFallBackToEnglish() {
        val keys = setOf(
            TextKey.ProfileLoadError,
            TextKey.InviteToClan,
            TextKey.EditProfile,
            TextKey.Nickname,
            TextKey.UploadImage,
            TextKey.Matches,
            TextKey.Wins,
            TextKey.FeaturedAchievements,
            TextKey.BestStreak,
            TextKey.AverageReactionShort,
            TextKey.PerformanceMilestones,
            TextKey.WalletHistorySubtitle,
            TextKey.CheckInHistorySubtitle,
            TextKey.MissionsSubtitle,
            TextKey.CollectionSubtitle,
            TextKey.MatchHistory,
            TextKey.LoginDevices,
            TextKey.AccountSecurity,
            TextKey.CopyPlayerCode,
            TextKey.ProfilePerformanceHero,
            TextKey.ProfilePerformanceDescription,
            TextKey.WalletFlowHero,
            TextKey.WalletFlowDescription,
            TextKey.AttendanceJourney,
            TextKey.AttendanceJourneyDescription,
            TextKey.TodayMissions,
            TextKey.TodayMissionsDescription,
            TextKey.PersonalCollectionHero,
            TextKey.PersonalCollectionDescription,
            TextKey.RecentCompetitionHero,
            TextKey.RecentCompetitionDescription,
        )
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts
        allLocalizationCatalogs.filterKeys { it != AppLanguage.ENGLISH }.forEach { (language, catalog) ->
            keys.forEach { key ->
                assertNotEquals(english.getValue(key), catalog.texts.getValue(key), "${language.code}/$key")
            }
        }
    }
}
