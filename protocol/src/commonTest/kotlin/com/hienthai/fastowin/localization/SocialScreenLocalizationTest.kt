package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class SocialScreenLocalizationTest {
    @Test
    fun `public tournament copy is explicit in English and Vietnamese`() {
        val copy = listOf(
            Triple("TournamentVisibilityLabel", "Visibility", "Quyền riêng tư"),
            Triple("TournamentPublic", "Public", "Công khai"),
            Triple("TournamentPrivate", "Private", "Riêng tư"),
            Triple("CreateTournamentTitle", "Create tournament", "Tạo giải đấu"),
            Triple("PublicTournaments", "Public tournaments", "Giải đấu công khai"),
            Triple("PublicTournamentsEmpty", "No open tournaments match your filters. Try other filters or create a tournament.", "Không có giải đang mở phù hợp. Hãy đổi bộ lọc hoặc tạo giải mới."),
            Triple("PublicTournamentsLoading", "Loading public tournaments…", "Đang tải giải đấu công khai…"),
            Triple("PublicTournamentsReconnect", "Waiting for a connection. Reconnect to refresh and join tournaments.", "Đang chờ kết nối. Kết nối lại để tải và tham gia giải đấu."),
            Triple("PublicTournamentSearch", "Search tournament names", "Tìm theo tên giải đấu"),
            Triple("PublicTournamentRefresh", "Refresh tournaments", "Làm mới danh sách giải"),
            Triple("TournamentFeeFilterLabel", "Entry fee", "Phí tham gia"),
            Triple("TournamentPaid", "Paid", "Có phí"),
            Triple("PublicTournamentHost", "Hosted by {host}", "Chủ giải: {host}"),
            Triple("PublicTournamentSummary", "{mode} • {current}/{max} players • Prize: {prize} Gold", "{mode} • {current}/{max} người • Giải thưởng: {prize} Vàng"),
            Triple("PublicTournamentEntryFee", "Entry: {fee} Gold", "Phí tham gia: {fee} Vàng"),
            Triple("PublicTournamentJoinTitle", "Join tournament?", "Tham gia giải đấu?"),
            Triple("PublicTournamentJoinDescription", "Join {tournament} for {fee} Gold? Current prize pool: {prize} Gold. The fee is deducted when you join.", "Tham gia {tournament} với phí {fee} Vàng? Quỹ thưởng hiện tại: {prize} Vàng. Phí sẽ được trừ khi bạn tham gia."),
            Triple("TournamentInviteEmpty", "No eligible friends to invite. Add friends first; offline friends can receive invitations too.", "Chưa có bạn phù hợp để mời. Hãy kết bạn trước; bạn bè ngoại tuyến cũng nhận được lời mời.")
        )
        copy.forEach { (name, english, vietnamese) ->
            val key = TextKey.entries.single { it.name == name }
            assertEquals(english, allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts.getValue(key), name)
            assertEquals(vietnamese, allLocalizationCatalogs.getValue(AppLanguage.VIETNAMESE).texts.getValue(key), name)
        }
    }

    @Test
    fun `paid confirmation renders name current fee and prize in both languages`() {
        val arguments = mapOf("tournament" to "Updated Cup", "fee" to 150, "prize" to 900)
        assertEquals(
            "Join Updated Cup for 150 Gold? Current prize pool: 900 Gold. The fee is deducted when you join.",
            LocalizationService(AppLanguage.ENGLISH).text(TextKey.PublicTournamentJoinDescription, arguments)
        )
        assertEquals(
            "Tham gia Updated Cup với phí 150 Vàng? Quỹ thưởng hiện tại: 900 Vàng. Phí sẽ được trừ khi bạn tham gia.",
            LocalizationService(AppLanguage.VIETNAMESE).text(TextKey.PublicTournamentJoinDescription, arguments)
        )
    }

    @Test
    fun `public tournament errors use actionable localized keys`() {
        val expected = mapOf(
            "TOURNAMENT_NOT_PUBLIC" to "ServerTournamentNotPublic",
            "TOURNAMENT_FULL" to "ServerTournamentFull",
            "TOURNAMENT_ALREADY_STARTED" to "ServerTournamentClosed",
            "TOURNAMENT_CANCELLED" to "TournamentCancelledNotice",
            "PLAYER_ALREADY_JOINED" to "ServerTournamentAlreadyJoined",
            "FRIEND_ALREADY_JOINED_TOURNAMENT" to "ServerFriendAlreadyJoinedTournament",
            "PLAYER_BUSY" to "ServerPlayerActivityConflict",
            "NOT_ENOUGH_GOLD" to "ServerNotEnoughGold",
            "TOURNAMENT_SAVE_FAILED" to "ServerTournamentSaveFailed"
        )
        expected.forEach { (code, keyName) ->
            val key = protocolTextKeyForCode(code)
            assertEquals(keyName, key?.name, code)
            listOf(AppLanguage.ENGLISH, AppLanguage.VIETNAMESE).forEach { language ->
                kotlin.test.assertTrue(allLocalizationCatalogs.getValue(language).texts.getValue(checkNotNull(key)).isNotBlank())
            }
        }
    }

    @Test
    fun `already joined errors distinguish self from invited friend in both languages`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to Pair(
                "You have already joined this tournament.",
                "This friend has already joined the tournament."
            ),
            AppLanguage.VIETNAMESE to Pair(
                "Bạn đã tham gia giải đấu này.",
                "Người bạn này đã tham gia giải đấu."
            )
        )
        expected.forEach { (language, messages) ->
            val catalog = allLocalizationCatalogs.getValue(language).texts
            assertEquals(messages.first, catalog.getValue(TextKey.ServerTournamentAlreadyJoined))
            assertEquals(messages.second, catalog.getValue(TextKey.ServerFriendAlreadyJoinedTournament))
        }
    }

    @Test
    fun `social and shop navigation copy resolves explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Room invitations", "Friend requests", "Find a clan", "Members", "Invitations", "Mark all as read", "Gems", "Gold"),
            AppLanguage.VIETNAMESE to listOf("Lời mời vào phòng", "Lời mời kết bạn", "Tìm bang hội", "Thành viên", "Lời mời tham gia", "Đánh dấu tất cả đã đọc", "Gem", "Vàng"),
        )
        val keys = listOf(
            TextKey.RoomInvitations,
            TextKey.FriendRequests,
            TextKey.SearchClan,
            TextKey.ClanMembers,
            TextKey.JoinInvitations,
            TextKey.MarkAllRead,
            TextKey.GemTab,
            TextKey.GoldTab,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `primary social and shop actions resolve explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Search clans", "CREATE CLAN", "Join", "LEAVE CLAN", "START TOURNAMENT", "CREATE TOURNAMENT", "Clear all"),
            AppLanguage.VIETNAMESE to listOf("Tìm kiếm bang hội", "TẠO BANG HỘI", "Xin vào", "RỜI BANG", "BẮT ĐẦU GIẢI ĐẤU", "BẮT ĐẦU TẠO GIẢI", "Xóa tất cả"),
        )
        val keys = listOf(
            TextKey.SearchClanAction,
            TextKey.CreateClan,
            TextKey.RequestToJoin,
            TextKey.LeaveClan,
            TextKey.StartTournament,
            TextKey.CreateTournamentAction,
            TextKey.ClearAllNotifications,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `social confirmations and empty states resolve explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Remove friend?", "No clans found", "Leave clan?", "Remove clan member?", "Tournament cancelled", "Clear all notifications?", "Delete notification?"),
            AppLanguage.VIETNAMESE to listOf("Hủy kết bạn?", "Chưa tìm thấy bang hội", "Rời bang?", "Mời thành viên rời bang?", "Giải đấu đã hủy", "Xóa tất cả thông báo?", "Xóa thông báo?"),
        )
        val keys = listOf(
            TextKey.RemoveFriendTitle,
            TextKey.NoClansFound,
            TextKey.LeaveClanTitle,
            TextKey.RemoveClanMemberTitle,
            TextKey.TournamentCancelled,
            TextKey.ClearAllNotificationsTitle,
            TextKey.DeleteNotificationTitle,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `social and shop descriptions resolve explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Connect with a player code and invite friends to a match.", "No teammates yet. Enter a player code to send an invitation.", "Join a clan, complete quests and climb the ranks together.", "Try another keyword or create your own clan.", "Gather 4, 8 or 16 contenders and reach for the trophy.", "Invitations, rewards and news will appear here.", "New items will arrive here soon."),
            AppLanguage.VIETNAMESE to listOf("Kết nối bằng mã người chơi và mời bạn vào trận.", "Chưa có đồng đội. Nhập mã người chơi để gửi lời mời.", "Gia nhập bang hội, hoàn thành nhiệm vụ và cùng nhau leo hạng.", "Thử từ khóa khác hoặc tạo bang của riêng bạn.", "Tập hợp 4, 8 hoặc 16 chiến binh và chạm tay vào cúp vô địch.", "Lời mời, phần thưởng và tin mới sẽ xuất hiện tại đây.", "Vật phẩm mới sẽ sớm xuất hiện tại quầy này."),
        )
        val keys = listOf(
            TextKey.YourSquadDescription,
            TextKey.NoFriendsDescription,
            TextKey.ClanTogetherDescription,
            TextKey.NoClansFoundDescription,
            TextKey.KnockoutArenaDescription,
            TextKey.EmptyInboxDescription,
            TextKey.RestockingDescription,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `clan donation summary resolves explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("CLAN PROGRESS", "DONATE", "Contribute Gold or Gems to grow your clan.", "Balance: {amount} {currency}", "Clan XP gained: {xp}", "Your contribution"),
            AppLanguage.VIETNAMESE to listOf("TIẾN TRÌNH BANG", "QUYÊN GÓP", "Góp Vàng hoặc Gem để cùng phát triển bang hội.", "Số dư: {amount} {currency}", "Bang nhận {xp} XP", "Bạn đã góp"),
        )
        val keys = listOf(
            TextKey.ClanProgress,
            TextKey.DonateToClan,
            TextKey.DonateToClanDescription,
            TextKey.DonationBalance,
            TextKey.DonationXpPreview,
            TextKey.YourContribution,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `clan donation feedback resolves explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Donations cannot be undone.", "Your clan gained {xp} XP.", "This donation was already completed.", "Choose a valid donation amount.", "You do not have enough {currency}.", "Join this clan before donating.", "Could not complete the donation. Please try again."),
            AppLanguage.VIETNAMESE to listOf("Khoản quyên góp không thể hoàn tác.", "Bang đã nhận {xp} XP.", "Khoản quyên góp này đã hoàn tất trước đó.", "Hãy chọn mức quyên góp hợp lệ.", "Bạn không đủ {currency}.", "Hãy tham gia bang trước khi quyên góp.", "Chưa thể quyên góp. Vui lòng thử lại."),
        )
        val keys = listOf(
            TextKey.DonationCannotUndo,
            TextKey.DonationApplied,
            TextKey.DonationDuplicate,
            TextKey.DonationInvalidAmount,
            TextKey.DonationInsufficientFunds,
            TextKey.DonationMembershipRequired,
            TextKey.DonationFailed,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `room and tournament invitations resolve explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Invited you to “{room}”", "ROOM INVITATION", "{player} is waiting for you", "ROOM", "Tournament invitation", "{host} invited you to “{tournament}” · {mode} · {players} players.", "{host} invited you · {mode} · {players} players"),
            AppLanguage.VIETNAMESE to listOf("Mời bạn vào “{room}”", "LỜI MỜI VÀO PHÒNG", "{player} đang chờ bạn", "PHÒNG", "Lời mời đấu giải", "{host} mời bạn tham gia “{tournament}” · {mode} · {players} người.", "{host} mời bạn · {mode} · {players} người"),
        )
        val keys = listOf(
            TextKey.RoomInvitationSummary,
            TextKey.RoomInvitationTitle,
            TextKey.WaitingForYou,
            TextKey.RoomLabel,
            TextKey.TournamentInvitationTitle,
            TextKey.TournamentInvitationDescription,
            TextKey.TournamentInviteCompact,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `clan member management resolves explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Join requests ({count})", "Approve", "{role} · {trophies} Elo", "Remove {player} from clan", "Choose Logo", "Leader", "Co-leader", "Member"),
            AppLanguage.VIETNAMESE to listOf("Yêu cầu tham gia ({count})", "Duyệt", "{role} · {trophies} Elo", "Mời {player} rời bang", "Chọn Logo", "Bang chủ", "Phó bang", "Thành viên"),
        )
        val keys = listOf(
            TextKey.JoinRequestsCount,
            TextKey.Approve,
            TextKey.ClanMemberSummary,
            TextKey.RemoveClanMemberNamed,
            TextKey.ChooseClanLogo,
            TextKey.ClanRoleLeader,
            TextKey.ClanRoleCoLeader,
            TextKey.ClanRoleMember,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }

    @Test
    fun `tournament phases resolve explicitly in Vietnamese and English`() {
        val expected = mapOf(
            AppLanguage.ENGLISH to listOf("Updating", "Gathering", "In progress", "Finished", "Cancelled", "WAITING", "PLAYING", "DONE"),
            AppLanguage.VIETNAMESE to listOf("Đang cập nhật", "Đang tập hợp", "Đang diễn ra", "Đã kết thúc", "Đã hủy", "CHỜ", "ĐANG ĐẤU", "XONG"),
        )
        val keys = listOf(
            TextKey.TournamentUpdating,
            TextKey.TournamentPhaseLobby,
            TextKey.TournamentPhaseRunning,
            TextKey.TournamentPhaseFinished,
            TextKey.TournamentPhaseCancelled,
            TextKey.MatchPending,
            TextKey.MatchPlaying,
            TextKey.MatchFinished,
        )

        expected.forEach { (language, translations) ->
            val catalog = allLocalizationCatalogs.getValue(language)
            assertEquals(translations, keys.map(catalog.texts::getValue), language.code)
        }
    }
}
