package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class GuestEntryLocalizationTest {
    @Test
    fun `guest entry and save progress copy resolves explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Hello, {player}!", "Ready to set a new record?", "What should we call you?", "This name is only used for this play session.", "Enter nickname", "Create an account to keep your name, Elo and history on Android/iOS.", "Choose room type", "{player}'s room", "SAVE PROGRESS WITH EMAIL"),
            AppLanguage.VIETNAMESE to listOf("Xin chào, {player}!", "Sẵn sàng phá kỷ lục mới?", "Bạn muốn được gọi là gì?", "Tên này chỉ dùng trong phiên chơi hiện tại.", "Nhập biệt danh", "Tạo tài khoản để giữ tên, Elo và lịch sử trên Android/iOS.", "Chọn loại phòng", "Phòng của {player}", "LƯU TIẾN TRÌNH BẰNG EMAIL"),
        )
        val keys = listOf(
            TextKey.GreetingPlayer,
            TextKey.ReadyForRecord,
            TextKey.GuestNameTitle,
            TextKey.GuestNameDescription,
            TextKey.EnterNickname,
            TextKey.SaveNameProgressDescription,
            TextKey.ChooseRoomType,
            TextKey.DefaultRoomName,
            TextKey.SaveProgressEmail,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }
}
