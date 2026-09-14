package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileFilterLocalizationTest {
    @Test
    fun `profile history filters resolve explicitly in all twelve languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("All", "Wins", "Losses", "Received", "Used"),
            AppLanguage.VIETNAMESE to listOf("Tất cả", "Thắng", "Thua", "Nhận", "Đã dùng"),
            AppLanguage.SIMPLIFIED_CHINESE to listOf("全部", "胜利", "失败", "已获得", "已使用"),
            AppLanguage.JAPANESE to listOf("すべて", "勝利", "敗北", "受取", "使用済み"),
            AppLanguage.KOREAN to listOf("전체", "승리", "패배", "획득", "사용함"),
            AppLanguage.SPANISH to listOf("Todo", "Victorias", "Derrotas", "Recibidos", "Usados"),
            AppLanguage.BRAZILIAN_PORTUGUESE to listOf("Todos", "Vitórias", "Derrotas", "Recebidos", "Usados"),
            AppLanguage.FRENCH to listOf("Tout", "Victoires", "Défaites", "Reçus", "Utilisés"),
            AppLanguage.GERMAN to listOf("Alle", "Siege", "Niederlagen", "Erhalten", "Verwendet"),
            AppLanguage.INDONESIAN to listOf("Semua", "Menang", "Kalah", "Diterima", "Digunakan"),
            AppLanguage.THAI to listOf("ทั้งหมด", "ชนะ", "แพ้", "ได้รับ", "ใช้แล้ว"),
            AppLanguage.RUSSIAN to listOf("Все", "Победы", "Поражения", "Получено", "Использовано"),
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
