package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileFilterLocalizationTest {
    @Test
    fun `profile history filters resolve explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("All", "Wins", "Losses", "Received", "Used"),
            AppLanguage.VIETNAMESE to listOf("Tất cả", "Thắng", "Thua", "Nhận", "Đã dùng"),
        )
        val keys = listOf(
            TextKey.RecentFilterAll,
            TextKey.RecentFilterWins,
            TextKey.RecentFilterLosses,
            TextKey.Received,
            TextKey.Used,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }
}
