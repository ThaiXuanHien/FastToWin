package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class FramePresenceLocalizationTest {
    @Test
    fun frameAndPresenceCopyDoesNotFallBackToEnglish() {
        val keys = listOf(
            TextKey.AvatarOfPlayer, TextKey.SeasonalFrame,
            TextKey.BronzeFrame, TextKey.SilverFrame, TextKey.GoldFrame,
            TextKey.PerfectFrame, TextKey.PersistentFrameName, TextKey.BasicFrame,
            TextKey.PresenceOffline, TextKey.PresenceOnline,
            TextKey.PresenceInRoom, TextKey.PresencePlaying,
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
