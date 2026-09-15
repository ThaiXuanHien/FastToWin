package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class ClanCreationLogoLocalizationTest {
    @Test
    fun clanCreationManagementAndLogoCopyDoesNotFallBackToEnglish() {
        val keys = listOf(
            TextKey.SearchClanPlaceholder, TextKey.CreateAction,
            TextKey.ClanSummary, TextKey.PendingApproval,
            TextKey.CreateClanTitle, TextKey.CreateClanDescription,
            TextKey.ClanLogo, TextKey.RemoveClanMemberAction,
            TextKey.ClanLogoShield, TextKey.ClanLogoSwords,
            TextKey.ClanLogoFlag, TextKey.ClanLogoDragon,
            TextKey.ClanLogoWolf, TextKey.ClanLogoEagle,
            TextKey.ClanLogoCrown, TextKey.ClanLogoNamed,
            TextKey.Individual, TextKey.YouSuffix,
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
