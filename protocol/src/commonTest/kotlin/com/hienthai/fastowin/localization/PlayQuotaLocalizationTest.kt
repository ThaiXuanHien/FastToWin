package com.hienthai.fastowin.localization

import com.hienthai.fastowin.localization.catalogs.playQuotaTexts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayQuotaLocalizationTest {
    @Test
    fun `play quota copy is explicit in every supported language`() {
        val keysByName = TextKey.entries.associateBy(TextKey::name)
        val expectedKeys = PLAY_QUOTA_KEY_NAMES.mapTo(mutableSetOf()) { keyName ->
            assertNotNull(keysByName[keyName], "Missing TextKey.$keyName")
        }

        AppLanguage.entries.forEach { language ->
            val explicitTexts = playQuotaTexts.getValue(language)
            assertEquals(expectedKeys, explicitTexts.keys, language.code)
            expectedKeys.forEach { key ->
                assertTrue(explicitTexts.getValue(key).isNotBlank(), "${language.code}/$key")
                assertEquals(
                    explicitTexts.getValue(key),
                    allLocalizationCatalogs.getValue(language).texts.getValue(key),
                    "${language.code}/$key"
                )
            }
        }
    }

    private companion object {
        val PLAY_QUOTA_KEY_NAMES = setOf(
            "OnlineMatchesRemaining",
            "OnlineQuotaExhaustedTitle",
            "OnlineQuotaExhaustedMessage",
            "WatchAdForTwoMatches",
            "RewardedAdGranted",
            "RewardedAdCancelled",
            "RewardedAdUnavailable",
            "RewardedAdUseMobile",
            "OnlineAccountRequired"
        )
    }
}
