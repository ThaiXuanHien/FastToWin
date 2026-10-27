package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class MatchmakingLocalizationTest {
    @Test
    fun `matchmaking status resolves explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Matchmaking", "Match found", "Finding an opponent…", "Prioritising players near your Elo.", "Finding players in the same mode.", "Elo range", "No Elo impact", "CANCEL MATCHMAKING"),
            AppLanguage.VIETNAMESE to listOf("Ghép đối thủ", "Đã ghép trận", "Đang tìm đối thủ…", "Ưu tiên người chơi có Elo gần bạn.", "Đang ghép người chơi cùng chế độ.", "Khoảng Elo", "Không ảnh hưởng Elo", "HỦY GHÉP TRẬN"),
        )
        val keys = listOf(
            TextKey.Matchmaking,
            TextKey.MatchFound,
            TextKey.FindingOpponent,
            TextKey.RankedMatchmakingHint,
            TextKey.CasualMatchmakingHint,
            TextKey.EloRange,
            TextKey.NoEloImpact,
            TextKey.CancelMatchmaking,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `public room list copy resolves explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("PUBLIC ROOMS", "Find the right opponent", "Create a private room or join a waiting room.", "Search room or host name", "All", "Waiting", "{count} rooms", "No rooms are waiting. Create one or pull down to refresh.", "No rooms match these filters."),
            AppLanguage.VIETNAMESE to listOf("PHÒNG CÔNG KHAI", "Tìm đối thủ phù hợp", "Tạo phòng riêng hoặc tham gia phòng đang chờ.", "Tìm tên phòng hoặc chủ phòng", "Tất cả", "Đang chờ", "{count} phòng", "Chưa có phòng đang chờ. Hãy tạo phòng mới hoặc kéo xuống để làm mới.", "Không tìm thấy phòng phù hợp với bộ lọc."),
        )
        val keys = listOf(
            TextKey.PublicRooms,
            TextKey.FindOpponent,
            TextKey.PublicRoomsDescription,
            TextKey.SearchRooms,
            TextKey.All,
            TextKey.Waiting,
            TextKey.RoomCount,
            TextKey.NoWaitingRooms,
            TextKey.NoMatchingRooms,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `room connection progress and invalid link resolve explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Reconnecting…", "Authenticating session…", "Connecting…", "The linked room no longer exists or is full."),
            AppLanguage.VIETNAMESE to listOf("Đang kết nối lại…", "Đang xác thực phiên chơi…", "Đang kết nối…", "Phòng trong liên kết không còn tồn tại hoặc đã đủ người."),
        )
        val keys = listOf(
            TextKey.Reconnecting,
            TextKey.AuthenticatingSession,
            TextKey.Connecting,
            TextKey.RoomLinkUnavailable,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }
}
