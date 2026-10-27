package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class RoomLobbyLocalizationTest {
    @Test
    fun `room lobby actions and readiness resolve explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Wait for the host to start the match.", "CANCEL READY", "READY", "Waiting for everyone to be ready…", "INVITE FRIENDS", "KICK", "CLOSE ROOM", "LEAVE ROOM", "READY", "WAITING"),
            AppLanguage.VIETNAMESE to listOf("Chờ chủ phòng bắt đầu trận.", "HỦY SẴN SÀNG", "SẴN SÀNG", "Đang chờ người khác sẵn sàng…", "MỜI BẠN BÈ", "MỜI RA", "ĐÓNG PHÒNG", "RỜI PHÒNG", "SẴN SÀNG", "ĐANG CHỜ"),
        )
        val keys = listOf(
            TextKey.WaitHostStart,
            TextKey.CancelReady,
            TextKey.ReadyAction,
            TextKey.WaitingForReady,
            TextKey.InviteFriends,
            TextKey.Kick,
            TextKey.CloseRoom,
            TextKey.LeaveRoom,
            TextKey.ReadyStatus,
            TextKey.WaitingStatus,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `room player labels resolve explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Players", "Blue Team", "Red Team", "You", "Teammate", "Opponent", "Waiting…", "Host • You", "Guest", "Host", "Waiting for player…"),
            AppLanguage.VIETNAMESE to listOf("Người chơi", "Đội Xanh", "Đội Đỏ", "Bạn", "Đồng đội", "Đối thủ", "Đang chờ…", "Chủ phòng • Bạn", "Khách", "Chủ phòng", "Đang chờ người chơi…"),
        )
        val keys = listOf(
            TextKey.PlayersTitle,
            TextKey.BlueTeam,
            TextKey.RedTeam,
            TextKey.You,
            TextKey.Teammate,
            TextKey.Opponent,
            TextKey.WaitingPlayer,
            TextKey.HostYou,
            TextKey.Guest,
            TextKey.Host,
            TextKey.WaitingForPlayer,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `room discovery and access copy resolves explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Enter room code", "Paste the code shared by the host.", "Room code", "FIND ROOM", "No room was found with this code.", "Create new room", "Room name", "Public", "Private", "Room password", "Join room"),
            AppLanguage.VIETNAMESE to listOf("Nhập mã phòng", "Dán mã được chủ phòng chia sẻ.", "Mã phòng", "TÌM PHÒNG", "Không tìm thấy phòng với mã này.", "Tạo phòng mới", "Tên phòng", "Công khai", "Riêng tư", "Mật khẩu phòng", "Tham gia phòng"),
        )
        val keys = listOf(
            TextKey.EnterRoomCode,
            TextKey.PasteRoomCode,
            TextKey.RoomCode,
            TextKey.FindRoom,
            TextKey.RoomCodeNotFound,
            TextKey.CreateNewRoom,
            TextKey.RoomName,
            TextKey.Public,
            TextKey.Private,
            TextKey.RoomPassword,
            TextKey.JoinRoomTitle,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `room visibility and match type copy resolves explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Players need a password.", "Anyone can join without a password.", "Ranked · Affects Elo", "Casual · No Elo impact", "Private room", "Public room", "{host} • {mode} • {matchType}", "ENTER", "Private room"),
            AppLanguage.VIETNAMESE to listOf("Người chơi cần nhập mật khẩu.", "Mọi người có thể tham gia, không cần mật khẩu.", "Đấu xếp hạng · Có ảnh hưởng Elo", "Đấu thường · Không ảnh hưởng Elo", "Phòng riêng", "Phòng công khai", "{host} • {mode} • {matchType}", "VÀO", "Phòng riêng"),
        )
        val keys = listOf(
            TextKey.PrivatePasswordRequired,
            TextKey.PublicNoPassword,
            TextKey.RankedEloImpact,
            TextKey.CasualNoEloImpact,
            TextKey.PrivateRoom,
            TextKey.PublicRoom,
            TextKey.RoomListSummary,
            TextKey.EnterRoom,
            TextKey.JoinPrivateRoom,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `joined room and sharing copy resolves explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Enter the password to join “{room}”.", "{room} • Hosted by {host}", "ROOM CODE • {code}", "IN ROOM", "Share the code to invite friends.", "{mode} • 50 numbers", "SHARE ROOM CODE", "Could not open sharing. Please try again."),
            AppLanguage.VIETNAMESE to listOf("Nhập mật khẩu để tham gia “{room}”.", "{room} • Chủ phòng {host}", "MÃ PHÒNG • {code}", "ĐÃ VÀO PHÒNG", "Chia sẻ mã để mời bạn bè.", "{mode} • 50 số", "CHIA SẺ MÃ PHÒNG", "Không thể mở bảng chia sẻ. Vui lòng thử lại."),
        )
        val keys = listOf(
            TextKey.PrivateJoinDescription,
            TextKey.PublicJoinDescription,
            TextKey.JoinedRoomCode,
            TextKey.JoinedRoom,
            TextKey.ShareCodeHint,
            TextKey.ModeNumberCount,
            TextKey.ShareRoomCode,
            TextKey.ShareSheetError,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `room connection and match start status resolves explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("{latency} ms • Good", "{latency} ms • Stable", "{latency} ms • Slow", "OPPONENT READY", "The match is about to start"),
            AppLanguage.VIETNAMESE to listOf("{latency} ms • Tốt", "{latency} ms • Ổn định", "{latency} ms • Chậm", "ĐỐI THỦ ĐÃ SẴN SÀNG", "Trận đấu sắp bắt đầu"),
        )
        val keys = listOf(
            TextKey.LatencyGood,
            TextKey.LatencyStable,
            TextKey.LatencySlow,
            TextKey.OpponentReady,
            TextKey.MatchStarting,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }
}
