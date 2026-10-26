package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeZoneDisplayLabelTest {
    @Test
    fun vietnamAliasesFollowTheSelectedLanguage() {
        val vietnamese = LocalizationService(AppLanguage.VIETNAMESE)
        val english = LocalizationService(AppLanguage.ENGLISH)
        for (zone in listOf("Asia/Bangkok", "Asia/Ho_Chi_Minh")) {
            assertEquals("Giờ Việt Nam (UTC+7)", vietnamese.timeZoneDisplayLabel(zone))
            assertEquals("Vietnam time (UTC+7)", english.timeZoneDisplayLabel(zone))
        }
    }

    @Test
    fun otherZoneIdentifiersPassThroughWithoutConversionOrNormalization() {
        for (language in AppLanguage.entries) {
            val localization = LocalizationService(language)
            for (zone in listOf("UTC", "America/New_York", "Europe/Paris", "Unknown/Zone", " Asia/Bangkok ")) {
                assertEquals(zone, localization.timeZoneDisplayLabel(zone))
            }
        }
    }
}
