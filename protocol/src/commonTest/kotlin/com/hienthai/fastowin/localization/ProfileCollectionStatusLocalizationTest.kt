package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class ProfileCollectionStatusLocalizationTest {
    @Test
    fun collectionStatusCopyDoesNotFallBackToEnglish() {
        assertLocalized(
            TextKey.Copied, TextKey.Rookie, TextKey.TitleValue,
            TextKey.PlayerCodeValue, TextKey.DifficultyElite,
            TextKey.EmptyCollection, TextKey.Titles, TextKey.Equipping,
            TextKey.Equipped, TextKey.TapToEquip, TextKey.Unlocked,
            TextKey.UnlockRequirement, TextKey.Locked,
            TextKey.NoCompletedMatches, TextKey.NoMatchesForFilter,
        )
    }

    @Test
    fun sessionAndActivityStatusCopyDoesNotFallBackToEnglish() {
        assertLocalized(
            TextKey.UpdatingPassword, TextKey.DeletingAccount,
            TextKey.LoginDevicesTitle, TextKey.RevokeDeviceTitle,
            TextKey.RevokeCurrentDeviceTitle, TextKey.UnknownDevice,
            TextKey.JustNow, TextKey.MinutesAgo, TextKey.HoursAgo,
            TextKey.DaysAgo, TextKey.SessionExpiresInDays, TextKey.ActivityTime,
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
