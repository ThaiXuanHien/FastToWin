package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey

internal val englishSocialShopQuantities = mapOf(
    QuantityKey.Warriors to mapOf(PluralCategory.ONE to "{count} player", PluralCategory.OTHER to "{count} players"),
    QuantityKey.Clans to mapOf(PluralCategory.ONE to "{count} clan", PluralCategory.OTHER to "{count} clans"),
    QuantityKey.Members to mapOf(PluralCategory.ONE to "{count} member", PluralCategory.OTHER to "{count} members")
)
internal val vietnameseSocialShopQuantities = mapOf(
    QuantityKey.Warriors to mapOf(PluralCategory.OTHER to "{count} chiến binh"), QuantityKey.Clans to mapOf(PluralCategory.OTHER to "{count} bang"), QuantityKey.Members to mapOf(PluralCategory.OTHER to "{count} thành viên")
)
internal val germanSocialShopQuantities = mapOf(
    QuantityKey.Warriors to mapOf(PluralCategory.ONE to "{count} Spieler", PluralCategory.OTHER to "{count} Spieler"), QuantityKey.Clans to mapOf(PluralCategory.ONE to "{count} Clan", PluralCategory.OTHER to "{count} Clans"), QuantityKey.Members to mapOf(PluralCategory.ONE to "{count} Mitglied", PluralCategory.OTHER to "{count} Mitglieder")
)
internal val japaneseSocialShopQuantities = mapOf(
    QuantityKey.Warriors to mapOf(PluralCategory.OTHER to "{count}人"), QuantityKey.Clans to mapOf(PluralCategory.OTHER to "{count}クラン"), QuantityKey.Members to mapOf(PluralCategory.OTHER to "{count}人のメンバー")
)
internal val simplifiedChineseSocialShopQuantities = mapOf(
    QuantityKey.Warriors to mapOf(PluralCategory.OTHER to "{count} 名玩家"), QuantityKey.Clans to mapOf(PluralCategory.OTHER to "{count} 个战队"), QuantityKey.Members to mapOf(PluralCategory.OTHER to "{count} 名成员")
)
internal val koreanSocialShopQuantities = mapOf(
    QuantityKey.Warriors to mapOf(PluralCategory.OTHER to "플레이어 {count}명"), QuantityKey.Clans to mapOf(PluralCategory.OTHER to "클랜 {count}개"), QuantityKey.Members to mapOf(PluralCategory.OTHER to "멤버 {count}명")
)
internal val spanishSocialShopQuantities = mapOf(
    QuantityKey.Warriors to mapOf(PluralCategory.ONE to "{count} jugador", PluralCategory.OTHER to "{count} jugadores"), QuantityKey.Clans to mapOf(PluralCategory.ONE to "{count} clan", PluralCategory.OTHER to "{count} clanes"), QuantityKey.Members to mapOf(PluralCategory.ONE to "{count} miembro", PluralCategory.OTHER to "{count} miembros")
)
internal val brazilianPortugueseSocialShopQuantities = mapOf(
    QuantityKey.Warriors to mapOf(PluralCategory.ONE to "{count} jogador", PluralCategory.OTHER to "{count} jogadores"), QuantityKey.Clans to mapOf(PluralCategory.ONE to "{count} clã", PluralCategory.OTHER to "{count} clãs"), QuantityKey.Members to mapOf(PluralCategory.ONE to "{count} membro", PluralCategory.OTHER to "{count} membros")
)
internal val frenchSocialShopQuantities = mapOf(
    QuantityKey.Warriors to mapOf(PluralCategory.ONE to "{count} joueur", PluralCategory.OTHER to "{count} joueurs"), QuantityKey.Clans to mapOf(PluralCategory.ONE to "{count} clan", PluralCategory.OTHER to "{count} clans"), QuantityKey.Members to mapOf(PluralCategory.ONE to "{count} membre", PluralCategory.OTHER to "{count} membres")
)
internal val indonesianSocialShopQuantities = mapOf(
    QuantityKey.Warriors to mapOf(PluralCategory.OTHER to "{count} pemain"), QuantityKey.Clans to mapOf(PluralCategory.OTHER to "{count} klan"), QuantityKey.Members to mapOf(PluralCategory.OTHER to "{count} anggota")
)
internal val thaiSocialShopQuantities = mapOf(
    QuantityKey.Warriors to mapOf(PluralCategory.OTHER to "ผู้เล่น {count} คน"), QuantityKey.Clans to mapOf(PluralCategory.OTHER to "{count} แคลน"), QuantityKey.Members to mapOf(PluralCategory.OTHER to "สมาชิก {count} คน")
)
internal val russianSocialShopQuantities = mapOf(
    QuantityKey.Warriors to mapOf(PluralCategory.ONE to "{count} игрок", PluralCategory.FEW to "{count} игрока", PluralCategory.MANY to "{count} игроков", PluralCategory.OTHER to "{count} игрока"),
    QuantityKey.Clans to mapOf(PluralCategory.ONE to "{count} клан", PluralCategory.FEW to "{count} клана", PluralCategory.MANY to "{count} кланов", PluralCategory.OTHER to "{count} клана"),
    QuantityKey.Members to mapOf(PluralCategory.ONE to "{count} участник", PluralCategory.FEW to "{count} участника", PluralCategory.MANY to "{count} участников", PluralCategory.OTHER to "{count} участника")
)

internal val englishSocialShopTexts = mapOf(
    TextKey.RemoveFriendTitle to "Remove friend?", TextKey.RemoveFriendDescription to "You and {player} will no longer be friends.",
    TextKey.RemoveFriendAction to "REMOVE FRIEND", TextKey.GoBack to "GO BACK",
    TextKey.BlockFriendDescription to "{player} will not be able to send you friend or room invitations.",
    TextKey.YourSquad to "Your squad", TextKey.YourSquadDescription to "Connect with a player code and invite friends to a match.",
    TextKey.RoomInvitations to "Room invitations", TextKey.NewCount to "{count} new",
    TextKey.RoomInvitationSummary to "Invited you to “{room}”", TextKey.FriendRequests to "Friend requests",
    TextKey.PlayerCodeLabel to "Player code", TextKey.PlayerCodeShort to "Code: {code}", TextKey.PlayerCodeExample to "e.g. FTW8X2Q",
    TextKey.BlockPlayerNamed to "Block {player}", TextKey.FriendList to "Friends",
    TextKey.ActiveFriends to "{count} online", TextKey.NoFriendsDescription to "No teammates yet. Enter a player code to send an invitation.",
    TextKey.PendingReplies to "Awaiting replies", TextKey.SentFriendInvitation to "Invitation sent · {code}",
    TextKey.BlockedPlayers to "Blocked", TextKey.Unblock to "UNBLOCK",
    TextKey.PlayerActions to "Actions for {player}", TextKey.InviteSent to "Invited",
    TextKey.SendingInvitation to "Sending…", TextKey.InviteToRoom to "Invite to room",
    TextKey.RoomInvitationTitle to "ROOM INVITATION", TextKey.WaitingForYou to "{player} is waiting for you",
    TextKey.RoomLabel to "ROOM", TextKey.SocialHub to "SOCIAL HUB",

    TextKey.ClanTogetherTitle to "Compete together", TextKey.ClanTogetherDescription to "Join a clan, complete quests and climb the ranks together.",
    TextKey.SearchClan to "Find a clan", TextKey.SearchClanPlaceholder to "Enter clan name…", TextKey.SearchClanAction to "Search clans",
    TextKey.CreateClan to "CREATE CLAN", TextKey.CreateAction to "CREATE", TextKey.ExploreClans to "Explore clans", TextKey.NoClansFound to "No clans found",
    TextKey.NoClansFoundDescription to "Try another keyword or create your own clan.",
    TextKey.ClanSummary to "{members} members · {trophies} trophies", TextKey.PendingApproval to "Pending",
    TextKey.RequestToJoin to "Join", TextKey.CreateClanTitle to "Create Clan",
    TextKey.CreateClanDescription to "Choose a short, memorable name so teammates can find you.",
    TextKey.ClanName to "Clan name", TextKey.ClanDescription to "Clan description",
    TextKey.DefaultClanDescription to "Conquer the leaderboard with your teammates.",
    TextKey.SelectClanLogo to "Choose clan logo", TextKey.ClanLogo to "Clan logo",
    TextKey.JoinRequestsCount to "Join requests ({count})", TextKey.ClanMembers to "Members",
    TextKey.LeaveClan to "LEAVE CLAN", TextKey.LeaveClanTitle to "Leave clan?",
    TextKey.LeaveClanDescription to "You will leave {clan}.", TextKey.RemoveClanMemberTitle to "Remove clan member?",
    TextKey.RemoveClanMemberDescription to "{player} will be removed from {clan}.", TextKey.RemoveClanMemberAction to "REMOVE MEMBER",
    TextKey.ClanTrophies to "Clan trophies", TextKey.MemberTrophies to "Member trophies", TextKey.TotalTrophies to "Total trophies",
    TextKey.WeeklyClanQuest to "WEEKLY QUEST", TextKey.WinClanMatches to "Win {count} matches with your clan",
    TextKey.Approve to "Approve", TextKey.ClanMemberSummary to "{role} · {trophies} trophies",
    TextKey.RemoveClanMemberNamed to "Remove {player} from clan", TextKey.ChooseClanLogo to "Choose Logo",
    TextKey.ChooseClanLogoDescription to "Tap an icon to apply it immediately.",
    TextKey.ClanRoleLeader to "Leader", TextKey.ClanRoleCoLeader to "Co-leader", TextKey.ClanRoleMember to "Member",
    TextKey.ClanLogoShield to "Shield", TextKey.ClanLogoSwords to "Crossed swords", TextKey.ClanLogoFlag to "Flag",
    TextKey.ClanLogoDragon to "Dragon", TextKey.ClanLogoWolf to "Wolf", TextKey.ClanLogoEagle to "Eagle", TextKey.ClanLogoCrown to "Crown", TextKey.ClanLogoNamed to "Logo {logo}",

    TextKey.Individual to "Players", TextKey.FameRace to "Race for glory",
    TextKey.FameRaceDescription to "Climb tiers, protect your win streak and take the top spot.",
    TextKey.CurrentSeason to "Current season", TextKey.PreviousSeason to "Previous season", TextKey.AllTime to "All time", TextKey.History to "History",
    TextKey.TopPlayers to "Top players", TextKey.WarriorsCount to "{count} players",
    TextKey.CurrentSeasonLeaderboardEmpty to "No player has completed placement this season.",
    TextKey.PreviousSeasonLeaderboardEmpty to "No player qualified for the previous season ranking.",
    TextKey.AllTimeLeaderboardEmpty to "No player has completed a match yet.", TextKey.YourPosition to "YOUR POSITION",
    TextKey.StrongestClans to "Strongest clans", TextKey.StrongestClansDescription to "Combined Elo strength of every clan member.",
    TextKey.TopClans to "Top clans", TextKey.ClansCount to "{count} clans", TextKey.ClanLeaderboardEmpty to "No clans are ranked yet.",
    TextKey.YourClan to "YOUR CLAN", TextKey.RankingAwaiting to "The ranking awaits", TextKey.YouSuffix to " · You",
    TextKey.MembersCount to "{count} members", TextKey.TotalElo to "Total Elo",
    TextKey.LeaderboardPlayerSummary to "{tier} · {wins} wins · {rate}%",

    TextKey.KnockoutArena to "Knockout arena", TextKey.KnockoutArenaDescription to "Gather 4, 8 or 16 contenders and reach for the trophy.",
    TextKey.TournamentLobby to "Tournament lobby", TextKey.ChampionName to "Champion: {player}", TextKey.TournamentUpdating to "Updating",
    TextKey.TournamentCancelled to "Tournament cancelled", TextKey.TournamentLobbySummary to "{mode} · {current}/{max} players · {prize} Gold",
    TextKey.BracketInProgress to "The bracket is in progress. Winners advance to the next round.",
    TextKey.TournamentPrizeAwarded to "The {prize} Gold prize has been awarded.", TextKey.CreateAnotherTournament to "You can create another tournament now.",
    TextKey.JoinInvitations to "Invitations", TextKey.RecentTournaments to "Recent tournaments",
    TextKey.TournamentInvitationTitle to "Tournament invitation",
    TextKey.TournamentInvitationDescription to "{host} invited you to “{tournament}” · {mode} · {players} players.",
    TextKey.DecideLater to "LATER", TextKey.ChooseTournamentMode to "Choose tournament mode",
    TextKey.CreatePrivateTournament to "Create private tournament", TextKey.TournamentSize to "Tournament size",
    TextKey.TournamentFourFormat to "4 players • 2 semifinals • 1 final", TextKey.TournamentEightFormat to "8 players • 4 quarterfinals • 2 semifinals • 1 final",
    TextKey.TournamentSixteenFormat to "16 players • 8 round-of-16 • 4 quarterfinals • 2 semifinals • 1 final",
    TextKey.TournamentName to "Tournament name", TextKey.TournamentNameExample to "e.g. Speed Champions Cup",
    TextKey.GameModeLabel to "Game mode", TextKey.EntryFeeGold to "Entry fee (Gold)", TextKey.Free to "Free", TextKey.Custom to "Custom",
    TextKey.EnterEntryFee to "Enter Gold entry fee", TextKey.EntryFeeExample to "e.g. 750", TextKey.CreateTournamentAction to "CREATE TOURNAMENT", TextKey.TournamentNoElo to "Tournament matches do not affect Elo.",
    TextKey.PlayerCountUpper to "{count} PLAYERS", TextKey.EntryFee to "Entry fee", TextKey.Prize to "Prize", TextKey.GoldAmount to "{count} Gold",
    TextKey.Participants to "Participants", TextKey.WaitingForPlayerEllipsis to "Waiting for player…", TextKey.TournamentHost to "Host",
    TextKey.StartTournament to "START TOURNAMENT", TextKey.Bracket to "Bracket", TextKey.CancelTournament to "CANCEL TOURNAMENT",
    TextKey.LeaveTournament to "LEAVE TOURNAMENT", TextKey.AutomaticMatchesDescription to "Matches are created automatically. Winners advance to the next round.",
    TextKey.Champion to "CHAMPION", TextKey.FinalRound to "FINAL", TextKey.SemifinalRound to "SEMIFINALS",
    TextKey.QuarterfinalRound to "QUARTERFINALS", TextKey.RoundOfSixteen to "ROUND OF 16", TextKey.RoundNumber to "ROUND {round}",
    TextKey.MatchPending to "WAITING", TextKey.MatchPlaying to "PLAYING", TextKey.WaitingEllipsis to "Waiting…",
    TextKey.TournamentInviteCompact to "{host} invited you · {mode} · {players} players", TextKey.ChampionCompact to "Champion: {player}",
    TextKey.HideBracket to "Hide bracket", TextKey.TournamentPhaseLobby to "Gathering", TextKey.TournamentPhaseRunning to "In progress",
    TextKey.TournamentPhaseFinished to "Finished", TextKey.TournamentPhaseCancelled to "Cancelled", TextKey.JoinTournament to "JOIN",
    TextKey.InviteAction to "Invite", TextKey.MatchFinished to "DONE",

    TextKey.ClearAllNotificationsTitle to "Clear all notifications?", TextKey.ClearAllNotificationsDescription to "Visible notifications will be removed and synced across your devices.",
    TextKey.ClearAllNotifications to "Clear all", TextKey.ClearAllNotificationsA11y to "Clear all notifications", TextKey.MarkAllRead to "Mark all as read", TextKey.EmptyInbox to "Your inbox is empty",
    TextKey.EmptyInboxDescription to "Invitations, rewards and news will appear here.", TextKey.NotificationsHero to "News for you",
    TextKey.NotificationsHeroDescription to "Follow invitations, rewards and important activity.", TextKey.DeleteNotificationTitle to "Delete notification?",
    TextKey.DeleteNotificationDescription to "“{title}” will be removed from the list.", TextKey.DeleteNotification to "Delete notification",

    TextKey.GemTab to "Gems", TextKey.NumberSkins to "Number Skins", TextKey.NumberBoards to "Number Boards",
    TextKey.GemVault to "Gem Vault", TextKey.ArcadeVault to "Arcade Vault", TextKey.GemVaultDescription to "Gems unlock rare items and sync securely through the Store.",
    TextKey.ArcadeVaultDescription to "Unlock a fresh look and make every match your own.", TextKey.Restocking to "Restocking",
    TextKey.RestockingDescription to "New items will arrive here soon.", TextKey.ChooseGemPackage to "Choose a Gem pack",
    TextKey.GemPackageDescription to "Gems unlock rare items. Prices come from your Store account.", TextKey.LoginToBuyGems to "Sign in to buy and sync Gems across devices.",
    TextKey.PriceUnavailable to "Price unavailable", TextKey.EarnGemsDescription to "You can also earn Gems through check-ins, hard quests and special achievements.",
    TextKey.Popular to "POPULAR", TextKey.EquippingNow to "EQUIPPED", TextKey.Equip to "EQUIP", TextKey.GemAmount to "{count} Gems",
    TextKey.ShopItemGoldName to "Golden Number Skin", TextKey.ShopItemDiamondName to "Diamond Number Skin",
    TextKey.ShopBoardDarkName to "Dark Number Board", TextKey.ShopBoardForestName to "Forest Number Board",
    TextKey.BillingPlayNotReady to "Google Play Billing is not ready.", TextKey.BillingPlayReconnecting to "Reconnecting to Google Play…",
    TextKey.BillingPriceLoadFailed to "Could not load prices from Google Play.", TextKey.BillingProductsMissing to "Gem products have not been created in Google Play Console.",
    TextKey.BillingProductUnavailable to "This Gem pack is unavailable on Google Play.", TextKey.BillingLaunchFailed to "Could not open Google Play checkout.",
    TextKey.BillingCancelled to "Payment cancelled.", TextKey.BillingRestoring to "Restoring the previous purchase…",
    TextKey.BillingUnavailable to "Google Play Billing is unavailable on this device.", TextKey.BillingIncomplete to "Payment was not completed.",
    TextKey.BillingVerifying to "Verifying purchase…", TextKey.BillingPending to "Google Play is processing this payment.",
    TextKey.BillingSandboxVerifying to "Verifying sandbox purchase…", TextKey.BillingGemsAdded to "Gems added to your account.",
    TextKey.BillingFinishLater to "Gems were added; the purchase will be finalized later.", TextKey.BillingSandboxActive to "Using test payments.",
    TextKey.BillingStoreKitSandboxNotice to "StoreKit sandbox must be verified on macOS before release.",
    TextKey.BillingAppStoreNotConfigured to "App Store is not configured for production.",
    TextKey.BillingWebUnsupported to "Gem purchases are not supported on Web. Use the Android or iOS app."
)

internal val vietnameseSocialShopTexts = englishSocialShopTexts + mapOf(
    TextKey.RemoveFriendTitle to "Hủy kết bạn?", TextKey.RemoveFriendDescription to "Bạn và {player} sẽ không còn trong danh sách bạn bè.",
    TextKey.RemoveFriendAction to "Hủy kết bạn", TextKey.GoBack to "Quay lại", TextKey.BlockFriendDescription to "{player} sẽ không thể gửi lời mời kết bạn hoặc lời mời vào phòng cho bạn.",
    TextKey.YourSquad to "Biệt đội của bạn", TextKey.YourSquadDescription to "Kết nối bằng mã người chơi và mời bạn vào trận.", TextKey.RoomInvitations to "Lời mời vào phòng",
    TextKey.NewCount to "{count} mới", TextKey.RoomInvitationSummary to "Mời bạn vào “{room}”", TextKey.FriendRequests to "Lời mời kết bạn",
    TextKey.PlayerCodeLabel to "Mã người chơi", TextKey.PlayerCodeShort to "Mã: {code}", TextKey.PlayerCodeExample to "VD: FTW8X2Q", TextKey.BlockPlayerNamed to "Chặn {player}",
    TextKey.FriendList to "Danh sách bạn bè", TextKey.ActiveFriends to "{count} online", TextKey.NoFriendsDescription to "Chưa có đồng đội. Nhập mã người chơi để gửi lời mời.",
    TextKey.PendingReplies to "Đang chờ phản hồi", TextKey.SentFriendInvitation to "Đã gửi lời mời · {code}", TextKey.BlockedPlayers to "Đã chặn",
    TextKey.Unblock to "BỎ CHẶN", TextKey.PlayerActions to "Thao tác với {player}", TextKey.InviteSent to "Đã mời", TextKey.SendingInvitation to "Đang gửi…",
    TextKey.InviteToRoom to "Mời vào phòng", TextKey.RoomInvitationTitle to "LỜI MỜI VÀO PHÒNG", TextKey.WaitingForYou to "{player} đang chờ bạn", TextKey.RoomLabel to "PHÒNG", TextKey.SocialHub to "TRUNG TÂM BẠN BÈ",
    TextKey.ClanTogetherTitle to "Sát cánh tranh tài", TextKey.ClanTogetherDescription to "Gia nhập bang hội, hoàn thành nhiệm vụ và cùng nhau leo hạng.",
    TextKey.SearchClan to "Tìm bang hội", TextKey.SearchClanPlaceholder to "Nhập tên bang…", TextKey.SearchClanAction to "Tìm kiếm bang hội",
    TextKey.CreateClan to "TẠO BANG HỘI", TextKey.CreateAction to "TẠO", TextKey.ExploreClans to "Khám phá bang hội", TextKey.NoClansFound to "Chưa tìm thấy bang hội",
    TextKey.NoClansFoundDescription to "Thử từ khóa khác hoặc tạo bang của riêng bạn.", TextKey.ClanSummary to "{members} thành viên · {trophies} cúp",
    TextKey.PendingApproval to "Đang chờ", TextKey.RequestToJoin to "Xin vào", TextKey.CreateClanTitle to "Tạo Bang Hội",
    TextKey.CreateClanDescription to "Chọn tên ngắn gọn, dễ nhớ để đồng đội tìm thấy bạn.", TextKey.ClanName to "Tên bang", TextKey.ClanDescription to "Mô tả bang hội",
    TextKey.DefaultClanDescription to "Cùng đồng đội chinh phục bảng xếp hạng.", TextKey.SelectClanLogo to "Chọn logo bang", TextKey.ClanLogo to "Logo bang",
    TextKey.JoinRequestsCount to "Yêu cầu tham gia ({count})", TextKey.ClanMembers to "Thành viên", TextKey.LeaveClan to "RỜI BANG",
    TextKey.LeaveClanTitle to "Rời bang?", TextKey.LeaveClanDescription to "Bạn sẽ rời khỏi {clan}.", TextKey.RemoveClanMemberTitle to "Mời thành viên rời bang?",
    TextKey.RemoveClanMemberDescription to "{player} sẽ bị xóa khỏi bang {clan}.", TextKey.RemoveClanMemberAction to "MỜI RỜI BANG",
    TextKey.ClanTrophies to "Cúp bang", TextKey.MemberTrophies to "Cúp thành viên", TextKey.TotalTrophies to "Tổng cúp",
    TextKey.WeeklyClanQuest to "NHIỆM VỤ TUẦN", TextKey.WinClanMatches to "Thắng {count} trận cùng bang hội", TextKey.Approve to "Duyệt",
    TextKey.ClanMemberSummary to "{role} · {trophies} cúp", TextKey.RemoveClanMemberNamed to "Mời {player} rời bang", TextKey.ChooseClanLogo to "Chọn Logo",
    TextKey.ChooseClanLogoDescription to "Chạm vào biểu tượng để áp dụng ngay.", TextKey.ClanRoleLeader to "Bang chủ", TextKey.ClanRoleCoLeader to "Phó bang",
    TextKey.ClanRoleMember to "Thành viên", TextKey.ClanLogoShield to "Khiên", TextKey.ClanLogoSwords to "Song kiếm", TextKey.ClanLogoFlag to "Cờ",
    TextKey.ClanLogoDragon to "Rồng", TextKey.ClanLogoWolf to "Sói", TextKey.ClanLogoEagle to "Đại bàng", TextKey.ClanLogoCrown to "Vương miện", TextKey.ClanLogoNamed to "Logo {logo}",
    TextKey.Individual to "Cá nhân", TextKey.FameRace to "Đường đua danh vọng", TextKey.FameRaceDescription to "Leo bậc, giữ chuỗi thắng và chiếm vị trí cao nhất.",
    TextKey.CurrentSeason to "Hiện tại", TextKey.PreviousSeason to "Mùa trước", TextKey.AllTime to "Toàn thời gian", TextKey.History to "Lịch sử",
    TextKey.TopPlayers to "Top người chơi", TextKey.WarriorsCount to "{count} chiến binh", TextKey.CurrentSeasonLeaderboardEmpty to "Chưa có người chơi hoàn thành phân hạng mùa này.",
    TextKey.PreviousSeasonLeaderboardEmpty to "Mùa trước chưa có người chơi đủ điều kiện xếp hạng.", TextKey.AllTimeLeaderboardEmpty to "Chưa có người chơi hoàn thành trận đấu.",
    TextKey.YourPosition to "VỊ TRÍ CỦA BẠN", TextKey.StrongestClans to "Bang hội mạnh nhất", TextKey.StrongestClansDescription to "Tổng hợp sức mạnh Elo của toàn bộ thành viên.",
    TextKey.TopClans to "Top bang hội", TextKey.ClansCount to "{count} bang", TextKey.ClanLeaderboardEmpty to "Chưa có bang hội nào trên bảng xếp hạng.",
    TextKey.YourClan to "BANG CỦA BẠN", TextKey.RankingAwaiting to "Đường đua đang chờ", TextKey.YouSuffix to " · Bạn", TextKey.MembersCount to "{count} thành viên",
    TextKey.TotalElo to "Tổng Elo", TextKey.LeaderboardPlayerSummary to "{tier} · {wins} thắng · {rate}%",
    TextKey.KnockoutArena to "Đấu trường loại trực tiếp", TextKey.KnockoutArenaDescription to "Tập hợp 4, 8 hoặc 16 chiến binh và chạm tay vào cúp vô địch.",
    TextKey.TournamentLobby to "Sảnh giải đấu", TextKey.ChampionName to "Nhà vô địch: {player}", TextKey.TournamentUpdating to "Đang cập nhật", TextKey.TournamentCancelled to "Giải đấu đã hủy",
    TextKey.TournamentLobbySummary to "{mode} · {current}/{max} người · {prize} vàng", TextKey.BracketInProgress to "Nhánh đấu đang diễn ra. Người thắng sẽ tiến vào vòng tiếp theo.",
    TextKey.TournamentPrizeAwarded to "Phần thưởng {prize} vàng đã được trao.", TextKey.CreateAnotherTournament to "Bạn có thể tạo một giải đấu mới ngay bây giờ.",
    TextKey.JoinInvitations to "Lời mời tham gia", TextKey.RecentTournaments to "Giải gần đây", TextKey.TournamentInvitationTitle to "Lời mời đấu giải",
    TextKey.TournamentInvitationDescription to "{host} mời bạn tham gia “{tournament}” · {mode} · {players} người.", TextKey.DecideLater to "ĐỂ SAU",
    TextKey.ChooseTournamentMode to "Chọn chế độ đấu giải", TextKey.CreatePrivateTournament to "Tạo giải riêng", TextKey.TournamentSize to "Quy mô giải",
    TextKey.TournamentFourFormat to "4 người • 2 bán kết • 1 chung kết", TextKey.TournamentEightFormat to "8 người • 4 tứ kết • 2 bán kết • 1 chung kết",
    TextKey.TournamentSixteenFormat to "16 người • 8 vòng 1/8 • 4 tứ kết • 2 bán kết • 1 chung kết", TextKey.TournamentName to "Tên giải đấu",
    TextKey.TournamentNameExample to "VD: Cúp Chiến Thần", TextKey.GameModeLabel to "Chế độ chơi", TextKey.EntryFeeGold to "Lệ phí tham gia (Vàng)",
    TextKey.Free to "Miễn phí", TextKey.Custom to "Tùy chỉnh", TextKey.EnterEntryFee to "Nhập số vàng lệ phí", TextKey.EntryFeeExample to "VD: 750", TextKey.CreateTournamentAction to "BẮT ĐẦU TẠO GIẢI",
    TextKey.TournamentNoElo to "Các trận đấu giải không ảnh hưởng Elo.", TextKey.PlayerCountUpper to "{count} NGƯỜI", TextKey.EntryFee to "Lệ phí",
    TextKey.Prize to "Giải thưởng", TextKey.GoldAmount to "{count} Vàng", TextKey.Participants to "Người tham gia", TextKey.WaitingForPlayerEllipsis to "Đang chờ người chơi…",
    TextKey.TournamentHost to "Chủ giải", TextKey.StartTournament to "BẮT ĐẦU GIẢI ĐẤU", TextKey.Bracket to "Nhánh đấu", TextKey.CancelTournament to "HỦY GIẢI ĐẤU",
    TextKey.LeaveTournament to "RỜI KHỎI GIẢI", TextKey.AutomaticMatchesDescription to "Các trận đấu được tạo tự động. Người thắng sẽ tiến vào vòng tiếp theo.",
    TextKey.Champion to "NHÀ VÔ ĐỊCH", TextKey.FinalRound to "TRẬN CHUNG KẾT", TextKey.SemifinalRound to "VÒNG BÁN KẾT",
    TextKey.QuarterfinalRound to "VÒNG TỨ KẾT", TextKey.RoundOfSixteen to "VÒNG 1/8", TextKey.RoundNumber to "VÒNG {round}",
    TextKey.MatchPending to "CHỜ", TextKey.MatchPlaying to "ĐANG ĐẤU", TextKey.WaitingEllipsis to "Đang chờ…",
    TextKey.TournamentInviteCompact to "{host} mời bạn · {mode} · {players} người", TextKey.ChampionCompact to "Vô địch: {player}", TextKey.HideBracket to "Ẩn nhánh đấu",
    TextKey.TournamentPhaseLobby to "Đang tập hợp", TextKey.TournamentPhaseRunning to "Đang diễn ra", TextKey.TournamentPhaseFinished to "Đã kết thúc", TextKey.TournamentPhaseCancelled to "Đã hủy",
    TextKey.JoinTournament to "THAM GIA", TextKey.InviteAction to "Mời", TextKey.MatchFinished to "XONG",
    TextKey.ClearAllNotificationsTitle to "Xóa tất cả thông báo?", TextKey.ClearAllNotificationsDescription to "Các thông báo đang hiển thị sẽ bị xóa và đồng bộ trên mọi thiết bị.",
    TextKey.ClearAllNotifications to "Xóa tất cả", TextKey.ClearAllNotificationsA11y to "Xóa tất cả thông báo", TextKey.MarkAllRead to "Đánh dấu tất cả đã đọc", TextKey.EmptyInbox to "Hộp thư đang trống",
    TextKey.EmptyInboxDescription to "Lời mời, phần thưởng và tin mới sẽ xuất hiện tại đây.", TextKey.NotificationsHero to "Tin mới dành cho bạn",
    TextKey.NotificationsHeroDescription to "Theo dõi lời mời, phần thưởng và hoạt động quan trọng.", TextKey.DeleteNotificationTitle to "Xóa thông báo?",
    TextKey.DeleteNotificationDescription to "Thông báo “{title}” sẽ bị xóa khỏi danh sách.", TextKey.DeleteNotification to "Xóa thông báo",
    TextKey.GemTab to "Gem", TextKey.NumberSkins to "Mặt số", TextKey.NumberBoards to "Bàn số", TextKey.GemVault to "Kho Gem", TextKey.ArcadeVault to "Kho báu Arcade",
    TextKey.GemVaultDescription to "Gem mở khóa vật phẩm hiếm và đồng bộ an toàn qua Store.", TextKey.ArcadeVaultDescription to "Mở khóa diện mạo mới và tạo dấu ấn riêng trong mỗi trận đấu.",
    TextKey.Restocking to "Đang nhập hàng", TextKey.RestockingDescription to "Vật phẩm mới sẽ sớm xuất hiện tại quầy này.", TextKey.ChooseGemPackage to "Chọn gói Gem",
    TextKey.GemPackageDescription to "Gem mở khóa vật phẩm hiếm. Giá được hiển thị theo tài khoản Store của bạn.", TextKey.LoginToBuyGems to "Đăng nhập để mua và đồng bộ Gem trên các thiết bị.",
    TextKey.PriceUnavailable to "Chưa có giá", TextKey.EarnGemsDescription to "Bạn cũng có thể săn Gem qua điểm danh, nhiệm vụ khó và thành tích đặc biệt.",
    TextKey.Popular to "PHỔ BIẾN", TextKey.EquippingNow to "ĐANG TRANG BỊ", TextKey.Equip to "TRANG BỊ", TextKey.GemAmount to "{count} Gem",
    TextKey.ShopItemGoldName to "Mặt số Hoàng Kim", TextKey.ShopItemDiamondName to "Mặt số Kim Cương", TextKey.ShopBoardDarkName to "Bàn số Bóng Đêm", TextKey.ShopBoardForestName to "Bàn số Rừng Xanh",
    TextKey.BillingPlayNotReady to "Google Play Billing chưa sẵn sàng.", TextKey.BillingPlayReconnecting to "Đang kết nối lại Google Play…",
    TextKey.BillingPriceLoadFailed to "Không tải được giá từ Google Play.", TextKey.BillingProductsMissing to "Các gói Gem chưa được tạo trên Google Play Console.",
    TextKey.BillingProductUnavailable to "Gói Gem này chưa sẵn sàng trên Google Play.", TextKey.BillingLaunchFailed to "Không thể mở thanh toán Google Play.",
    TextKey.BillingCancelled to "Đã hủy thanh toán.", TextKey.BillingRestoring to "Đang khôi phục giao dịch trước…", TextKey.BillingUnavailable to "Google Play Billing không khả dụng trên thiết bị.",
    TextKey.BillingIncomplete to "Thanh toán chưa hoàn tất.", TextKey.BillingVerifying to "Đang xác thực giao dịch…", TextKey.BillingPending to "Thanh toán đang chờ Google Play xử lý.",
    TextKey.BillingSandboxVerifying to "Đang xác thực giao dịch sandbox…", TextKey.BillingGemsAdded to "Đã cộng Gem vào tài khoản.",
    TextKey.BillingFinishLater to "Gem đã được cộng; giao dịch sẽ được hoàn tất lại sau.", TextKey.BillingSandboxActive to "Đang dùng thanh toán thử nghiệm.",
    TextKey.BillingStoreKitSandboxNotice to "StoreKit sandbox cần được xác nhận trên macOS trước khi phát hành.",
    TextKey.BillingAppStoreNotConfigured to "App Store chưa được cấu hình cho bản production.", TextKey.BillingWebUnsupported to "Mua Gem trên web chưa được hỗ trợ. Hãy dùng ứng dụng Android hoặc iOS."
)

internal val germanSocialShopTexts = englishSocialShopTexts + mapOf(
    TextKey.PlayerCodeExample to "z. B. FTW8X2Q", TextKey.YourSquad to "Dein Team", TextKey.FriendList to "Freunde", TextKey.ClanTogetherTitle to "Gemeinsam antreten", TextKey.ExploreClans to "Clans entdecken", TextKey.FameRace to "Rennen um Ruhm", TextKey.CurrentSeason to "Aktuelle Saison", TextKey.KnockoutArena to "K.-o.-Arena", TextKey.RecentTournaments to "Letzte Turniere", TextKey.NotificationsHero to "Neuigkeiten für dich", TextKey.EmptyInbox to "Dein Postfach ist leer", TextKey.NumberSkins to "Zahlenflächen", TextKey.NumberBoards to "Zahlenbretter", TextKey.GemVault to "Gem-Tresor", TextKey.ArcadeVault to "Arcade-Tresor", TextKey.Restocking to "Nachschub unterwegs", TextKey.Equip to "AUSRÜSTEN"
)
internal val japaneseSocialShopTexts = englishSocialShopTexts + mapOf(
    TextKey.PlayerCodeExample to "例：FTW8X2Q", TextKey.YourSquad to "あなたのチーム", TextKey.FriendList to "フレンド", TextKey.ClanTogetherTitle to "仲間と挑戦", TextKey.ExploreClans to "クランを探す", TextKey.FameRace to "栄光へのレース", TextKey.CurrentSeason to "今シーズン", TextKey.KnockoutArena to "勝ち抜きアリーナ", TextKey.RecentTournaments to "最近の大会", TextKey.NotificationsHero to "あなたへのお知らせ", TextKey.EmptyInbox to "通知はありません", TextKey.NumberSkins to "数字スキン", TextKey.NumberBoards to "数字ボード", TextKey.GemVault to "ジェム保管庫", TextKey.ArcadeVault to "アーケード保管庫", TextKey.Restocking to "入荷準備中", TextKey.Equip to "装備"
)
internal val simplifiedChineseSocialShopTexts = englishSocialShopTexts + mapOf(
    TextKey.PlayerCodeExample to "例如：FTW8X2Q", TextKey.YourSquad to "你的队伍", TextKey.FriendList to "好友列表", TextKey.ClanTogetherTitle to "并肩竞技", TextKey.ExploreClans to "探索战队", TextKey.FameRace to "荣耀竞速", TextKey.CurrentSeason to "当前赛季", TextKey.KnockoutArena to "淘汰赛场", TextKey.RecentTournaments to "最近赛事", TextKey.NotificationsHero to "你的新消息", TextKey.EmptyInbox to "暂无消息", TextKey.NumberSkins to "数字皮肤", TextKey.NumberBoards to "数字棋盘", TextKey.GemVault to "宝石仓库", TextKey.ArcadeVault to "街机仓库", TextKey.Restocking to "补货中", TextKey.Equip to "装备"
)
internal val koreanSocialShopTexts = englishSocialShopTexts + mapOf(
    TextKey.PlayerCodeExample to "예: FTW8X2Q", TextKey.YourSquad to "내 스쿼드", TextKey.FriendList to "친구", TextKey.ClanTogetherTitle to "함께 경쟁하세요", TextKey.ExploreClans to "클랜 탐색", TextKey.FameRace to "명예의 레이스", TextKey.CurrentSeason to "현재 시즌", TextKey.KnockoutArena to "토너먼트 아레나", TextKey.RecentTournaments to "최근 토너먼트", TextKey.NotificationsHero to "새 소식", TextKey.EmptyInbox to "알림이 없습니다", TextKey.NumberSkins to "숫자 스킨", TextKey.NumberBoards to "숫자 보드", TextKey.GemVault to "젬 보관소", TextKey.ArcadeVault to "아케이드 보관소", TextKey.Restocking to "상품 준비 중", TextKey.Equip to "장착"
)
internal val spanishSocialShopTexts = englishSocialShopTexts + mapOf(
    TextKey.PlayerCodeExample to "p. ej., FTW8X2Q", TextKey.YourSquad to "Tu equipo", TextKey.FriendList to "Amigos", TextKey.ClanTogetherTitle to "Compitan juntos", TextKey.ExploreClans to "Explorar clanes", TextKey.FameRace to "Carrera por la gloria", TextKey.CurrentSeason to "Temporada actual", TextKey.KnockoutArena to "Arena eliminatoria", TextKey.RecentTournaments to "Torneos recientes", TextKey.NotificationsHero to "Novedades para ti", TextKey.EmptyInbox to "Tu bandeja está vacía", TextKey.NumberSkins to "Diseños de números", TextKey.NumberBoards to "Tableros numéricos", TextKey.GemVault to "Cámara de Gemas", TextKey.ArcadeVault to "Cámara Arcade", TextKey.Restocking to "Reponiendo", TextKey.Equip to "EQUIPAR"
)
internal val brazilianPortugueseSocialShopTexts = englishSocialShopTexts + mapOf(
    TextKey.PlayerCodeExample to "ex.: FTW8X2Q", TextKey.YourSquad to "Seu esquadrão", TextKey.FriendList to "Amigos", TextKey.ClanTogetherTitle to "Compitam juntos", TextKey.ExploreClans to "Explorar clãs", TextKey.FameRace to "Corrida pela glória", TextKey.CurrentSeason to "Temporada atual", TextKey.KnockoutArena to "Arena eliminatória", TextKey.RecentTournaments to "Torneios recentes", TextKey.NotificationsHero to "Novidades para você", TextKey.EmptyInbox to "Sua caixa está vazia", TextKey.NumberSkins to "Visuais de números", TextKey.NumberBoards to "Tabuleiros numéricos", TextKey.GemVault to "Cofre de Gemas", TextKey.ArcadeVault to "Cofre Arcade", TextKey.Restocking to "Repondo estoque", TextKey.Equip to "EQUIPAR"
)
internal val frenchSocialShopTexts = englishSocialShopTexts + mapOf(
    TextKey.PlayerCodeExample to "ex. : FTW8X2Q", TextKey.YourSquad to "Ton équipe", TextKey.FriendList to "Amis", TextKey.ClanTogetherTitle to "Jouez ensemble", TextKey.ExploreClans to "Explorer les clans", TextKey.FameRace to "Course à la gloire", TextKey.CurrentSeason to "Saison actuelle", TextKey.KnockoutArena to "Arène à élimination", TextKey.RecentTournaments to "Tournois récents", TextKey.NotificationsHero to "Tes nouveautés", TextKey.EmptyInbox to "Ta boîte est vide", TextKey.NumberSkins to "Styles de nombres", TextKey.NumberBoards to "Plateaux numériques", TextKey.GemVault to "Coffre de Gemmes", TextKey.ArcadeVault to "Coffre Arcade", TextKey.Restocking to "Réapprovisionnement", TextKey.Equip to "ÉQUIPER"
)
internal val indonesianSocialShopTexts = englishSocialShopTexts + mapOf(
    TextKey.PlayerCodeExample to "mis. FTW8X2Q", TextKey.YourSquad to "Skuadmu", TextKey.FriendList to "Teman", TextKey.ClanTogetherTitle to "Bertanding bersama", TextKey.ExploreClans to "Jelajahi klan", TextKey.FameRace to "Perebutan kejayaan", TextKey.CurrentSeason to "Musim saat ini", TextKey.KnockoutArena to "Arena eliminasi", TextKey.RecentTournaments to "Turnamen terbaru", TextKey.NotificationsHero to "Kabar untukmu", TextKey.EmptyInbox to "Kotak masukmu kosong", TextKey.NumberSkins to "Skin angka", TextKey.NumberBoards to "Papan angka", TextKey.GemVault to "Brankas Gem", TextKey.ArcadeVault to "Brankas Arcade", TextKey.Restocking to "Mengisi stok", TextKey.Equip to "PAKAI"
)
internal val thaiSocialShopTexts = englishSocialShopTexts + mapOf(
    TextKey.PlayerCodeExample to "เช่น FTW8X2Q", TextKey.YourSquad to "ทีมของคุณ", TextKey.FriendList to "เพื่อน", TextKey.ClanTogetherTitle to "แข่งขันไปด้วยกัน", TextKey.ExploreClans to "สำรวจแคลน", TextKey.FameRace to "เส้นทางแห่งเกียรติยศ", TextKey.CurrentSeason to "ฤดูกาลปัจจุบัน", TextKey.KnockoutArena to "สนามน็อกเอาต์", TextKey.RecentTournaments to "ทัวร์นาเมนต์ล่าสุด", TextKey.NotificationsHero to "ข่าวสารสำหรับคุณ", TextKey.EmptyInbox to "กล่องข้อความว่าง", TextKey.NumberSkins to "สกินตัวเลข", TextKey.NumberBoards to "กระดานตัวเลข", TextKey.GemVault to "คลัง Gem", TextKey.ArcadeVault to "คลัง Arcade", TextKey.Restocking to "กำลังเติมสินค้า", TextKey.Equip to "สวมใส่"
)
internal val russianSocialShopTexts = englishSocialShopTexts + mapOf(
    TextKey.PlayerCodeExample to "например, FTW8X2Q", TextKey.YourSquad to "Твоя команда", TextKey.FriendList to "Друзья", TextKey.ClanTogetherTitle to "Сражайтесь вместе", TextKey.ExploreClans to "Поиск кланов", TextKey.FameRace to "Гонка за славой", TextKey.CurrentSeason to "Текущий сезон", TextKey.KnockoutArena to "Арена на выбывание", TextKey.RecentTournaments to "Недавние турниры", TextKey.NotificationsHero to "Новости для тебя", TextKey.EmptyInbox to "Уведомлений нет", TextKey.NumberSkins to "Скины чисел", TextKey.NumberBoards to "Числовые поля", TextKey.GemVault to "Хранилище самоцветов", TextKey.ArcadeVault to "Аркадное хранилище", TextKey.Restocking to "Скоро пополнение", TextKey.Equip to "НАДЕТЬ"
)
