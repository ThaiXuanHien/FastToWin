package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class ClanNotificationLocalizationTest {
    @Test
    fun clanAndNotificationConfirmationsDoNotFallBackToEnglish() {
        val keys = setOf(
            TextKey.ClearAllNotificationsDescription, TextKey.ClearAllNotificationsA11y,
            TextKey.EmptyInboxDescription, TextKey.NotificationsHeroDescription,
            TextKey.DeleteNotificationDescription, TextKey.DeleteNotification,
            TextKey.ClanTogetherDescription, TextKey.ClanName, TextKey.ClanDescription,
            TextKey.DefaultClanDescription, TextKey.SelectClanLogo,
            TextKey.JoinRequestsCount, TextKey.LeaveClanDescription,
            TextKey.RemoveClanMemberDescription, TextKey.ClanTrophies,
            TextKey.MemberTrophies, TextKey.TotalTrophies,
            TextKey.WeeklyClanQuest, TextKey.WinClanMatches,
            TextKey.ChooseClanLogoDescription,
        )
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts
        allLocalizationCatalogs.filterKeys { it != AppLanguage.ENGLISH }.forEach { (language, catalog) ->
            keys.forEach { key ->
                assertNotEquals(english.getValue(key), catalog.texts.getValue(key), "${language.code}/$key")
            }
        }
    }
}
