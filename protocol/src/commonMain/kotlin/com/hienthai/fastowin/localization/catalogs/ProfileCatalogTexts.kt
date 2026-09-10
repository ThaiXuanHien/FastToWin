package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey

internal val englishProfileQuantities = mapOf(
    QuantityKey.Matches to mapOf(PluralCategory.ONE to "{count} match", PluralCategory.OTHER to "{count} matches"),
    QuantityKey.Seasons to mapOf(PluralCategory.ONE to "{count} season", PluralCategory.OTHER to "{count} seasons")
)
internal val vietnameseProfileQuantities = mapOf(
    QuantityKey.Matches to mapOf(PluralCategory.OTHER to "{count} trận"),
    QuantityKey.Seasons to mapOf(PluralCategory.OTHER to "{count} mùa")
)
internal val spanishProfileQuantities = mapOf(
    QuantityKey.Matches to mapOf(PluralCategory.ONE to "{count} partida", PluralCategory.OTHER to "{count} partidas"),
    QuantityKey.Seasons to mapOf(PluralCategory.ONE to "{count} temporada", PluralCategory.OTHER to "{count} temporadas")
)
internal val brazilianPortugueseProfileQuantities = mapOf(
    QuantityKey.Matches to mapOf(PluralCategory.ONE to "{count} partida", PluralCategory.OTHER to "{count} partidas"),
    QuantityKey.Seasons to mapOf(PluralCategory.ONE to "{count} temporada", PluralCategory.OTHER to "{count} temporadas")
)
internal val frenchProfileQuantities = mapOf(
    QuantityKey.Matches to mapOf(PluralCategory.ONE to "{count} partie", PluralCategory.OTHER to "{count} parties"),
    QuantityKey.Seasons to mapOf(PluralCategory.ONE to "{count} saison", PluralCategory.OTHER to "{count} saisons")
)
internal val germanProfileQuantities = mapOf(
    QuantityKey.Matches to mapOf(PluralCategory.ONE to "{count} Spiel", PluralCategory.OTHER to "{count} Spiele"),
    QuantityKey.Seasons to mapOf(PluralCategory.ONE to "{count} Saison", PluralCategory.OTHER to "{count} Saisons")
)
internal val russianProfileQuantities = mapOf(
    QuantityKey.Matches to mapOf(PluralCategory.ONE to "{count} матч", PluralCategory.FEW to "{count} матча", PluralCategory.MANY to "{count} матчей", PluralCategory.OTHER to "{count} матча"),
    QuantityKey.Seasons to mapOf(PluralCategory.ONE to "{count} сезон", PluralCategory.FEW to "{count} сезона", PluralCategory.MANY to "{count} сезонов", PluralCategory.OTHER to "{count} сезона")
)
internal val simplifiedChineseProfileQuantities = mapOf(
    QuantityKey.Matches to mapOf(PluralCategory.OTHER to "{count} 场比赛"), QuantityKey.Seasons to mapOf(PluralCategory.OTHER to "{count} 个赛季")
)
internal val japaneseProfileQuantities = mapOf(
    QuantityKey.Matches to mapOf(PluralCategory.OTHER to "{count}試合"), QuantityKey.Seasons to mapOf(PluralCategory.OTHER to "{count}シーズン")
)
internal val koreanProfileQuantities = mapOf(
    QuantityKey.Matches to mapOf(PluralCategory.OTHER to "{count}경기"), QuantityKey.Seasons to mapOf(PluralCategory.OTHER to "{count}시즌")
)
internal val indonesianProfileQuantities = mapOf(
    QuantityKey.Matches to mapOf(PluralCategory.OTHER to "{count} pertandingan"), QuantityKey.Seasons to mapOf(PluralCategory.OTHER to "{count} musim")
)
internal val thaiProfileQuantities = mapOf(
    QuantityKey.Matches to mapOf(PluralCategory.OTHER to "{count} แมตช์"), QuantityKey.Seasons to mapOf(PluralCategory.OTHER to "{count} ฤดูกาล")
)

internal val englishProfileTexts = mapOf(
    TextKey.ProfileTitle to "Profile", TextKey.ExternalPlayerTitle to "Player",
    TextKey.ProfileLoadError to "Profile could not be loaded. Check your connection and try again.",
    TextKey.InviteToClan to "INVITE TO CLAN", TextKey.EditProfile to "Edit profile",
    TextKey.Nickname to "Nickname", TextKey.Avatar to "Avatar", TextKey.UploadImage to "Upload image",
    TextKey.Matches to "Matches", TextKey.Wins to "Wins", TextKey.FeaturedAchievements to "Highlights",
    TextKey.BestStreak to "Best streak", TextKey.AverageReactionShort to "Avg. reaction",
    TextKey.Activity to "Activity", TextKey.StatisticsAchievements to "Statistics and achievements",
    TextKey.PerformanceMilestones to "Performance and unlocked milestones",
    TextKey.WalletHistory to "Resource history", TextKey.WalletHistorySubtitle to "Gold, Gems and XP",
    TextKey.CheckInHistory to "Check-in history", TextKey.CheckInHistorySubtitle to "Calendar and streak milestones",
    TextKey.MissionsSubtitle to "Track progress and claim rewards", TextKey.CollectionTitle to "Collection",
    TextKey.CollectionSubtitle to "Frames and titles", TextKey.RecentMatches to "Recent matches",
    TextKey.MatchHistory to "Match history", TextKey.SettingsAccount to "Settings and account",
    TextKey.AppSettings to "App settings", TextKey.LoginDevices to "Signed-in devices",
    TextKey.AccountSecurity to "Account security", TextKey.Copied to "Copied", TextKey.Rookie to "Rookie",
    TextKey.TitleValue to "Title: {title}", TextKey.PlayerCodeValue to "Code: {code}",
    TextKey.CopyPlayerCode to "Copy player code", TextKey.ProfilePerformanceHero to "Your performance",
    TextKey.ProfilePerformanceDescription to "Track speed, accuracy and progress in every mode.",
    TextKey.WalletFlowHero to "Resource flow", TextKey.WalletFlowDescription to "Every Gold, Gem and XP gain or spend is recorded here.",
    TextKey.AttendanceJourney to "Attendance journey", TextKey.AttendanceJourneyDescription to "Review streaks, history and check-in milestones.",
    TextKey.TodayMissions to "Today's missions", TextKey.TodayMissionsDescription to "Complete challenges, claim rewards and level up.",
    TextKey.PersonalCollectionHero to "Your signature", TextKey.PersonalCollectionDescription to "Equip unlocked frames and titles.",
    TextKey.RecentCompetitionHero to "Match history", TextKey.RecentCompetitionDescription to "Review results and detailed performance.",
    TextKey.Received to "Received", TextKey.Used to "Used", TextKey.NoWalletTransactions to "No resource transactions yet.",
    TextKey.NoWalletTransactionsForFilter to "No transactions match this filter.",
    TextKey.DailyCheckInSource to "Daily check-in", TextKey.MissionRewardSource to "Mission reward",
    TextKey.MatchRewardSource to "Match reward", TextKey.ClanRewardSource to "Clan reward",
    TextKey.ShopPurchaseSource to "Shop purchase", TextKey.WalletOtherSource to "Other activity",
    TextKey.DailyMissions to "Daily", TextKey.WeeklyMissions to "Weekly", TextKey.NoMissions to "No new missions.",
    TextKey.MissionProgress to "{completed}/{total} completed", TextKey.Claimed to "Claimed",
    TextKey.Claiming to "CLAIMING", TextKey.ClaimReward to "CLAIM REWARD", TextKey.MissionCompleted to "Completed",
    TextKey.DifficultyEasy to "Easy", TextKey.DifficultyNormal to "Normal", TextKey.DifficultyHard to "Hard",
    TextKey.DifficultyElite to "Elite", TextKey.EmptyCollection to "Your collection is empty.", TextKey.Titles to "Titles",
    TextKey.Equipping to "Equipping…", TextKey.Equipped to "EQUIPPED", TextKey.TapToEquip to "Tap to equip",
    TextKey.Unlocked to "Unlocked", TextKey.UnlockRequirement to "Unlock: {requirement}", TextKey.Locked to "Locked",
    TextKey.RecentFilterAll to "All", TextKey.RecentFilterWins to "Wins", TextKey.RecentFilterLosses to "Losses",
    TextKey.NoCompletedMatches to "No completed matches yet.", TextKey.NoMatchesForFilter to "No matches match this filter.",
    TextKey.LogoutTitle to "Log out?", TextKey.LogoutDescription to "You will return to the sign-in screen on this device.",
    TextKey.LogoutAction to "LOG OUT", TextKey.AccountSecurityTitle to "ACCOUNT SECURITY",
    TextKey.AccountSecurityDescription to "Change your password or manage account deletion.",
    TextKey.ChangePassword to "Change password", TextKey.CurrentPassword to "Current password",
    TextKey.NewPasswordLabel to "New password", TextKey.ConfirmNewPasswordLabel to "Confirm new password",
    TextKey.NewPasswordMustDiffer to "The new password must differ from the current password.",
    TextKey.UpdatingPassword to "UPDATING…", TextKey.DangerZone to "Danger zone",
    TextKey.DeleteAccountWarning to "Deleting your account permanently removes your profile, Elo, history and achievements.",
    TextKey.PasswordToConfirm to "Enter password to confirm", TextKey.RequestDeleteAccount to "I WANT TO DELETE MY ACCOUNT",
    TextKey.DeletingAccount to "DELETING…", TextKey.LoginDevicesTitle to "SIGNED-IN DEVICES",
    TextKey.LoginDevicesDescription to "Review and revoke sessions you no longer use.",
    TextKey.NoActiveSessions to "No active sign-in sessions found.", TextKey.CurrentDevice to "This device",
    TextKey.LogoutAllDevices to "Log out all devices", TextKey.LogoutAllDevicesTitle to "LOG OUT ALL DEVICES?",
    TextKey.LogoutAllDevicesDescription to "All sessions, including this device, will be revoked and you will need to sign in again.",
    TextKey.RevokeDeviceTitle to "LOG OUT DEVICE?", TextKey.RevokeCurrentDeviceTitle to "LOG OUT THIS DEVICE?",
    TextKey.RevokeCurrentDeviceDescription to "You will return to the sign-in screen on this device.",
    TextKey.RevokeOtherDeviceDescription to "The session on {device} will be revoked immediately.",
    TextKey.UnknownDevice to "Unknown device", TextKey.JustNow to "just now", TextKey.MinutesAgo to "{count} min ago",
    TextKey.HoursAgo to "{count} hr ago", TextKey.DaysAgo to "{count} days ago",
    TextKey.SessionExpiresInDays to "Session expires in about {count} days", TextKey.ActivityTime to "Active {time}",
    TextKey.CheckIn to "Check-in", TextKey.StreakDays to "{count}-day streak",
    TextKey.CheckInCalendarDescription to "Check-in calendar · Up to the last 12 months",
    TextKey.PreviousMonth to "Previous month", TextKey.NextMonth to "Next month", TextKey.MonthYear to "Month {month}/{year}",
    TextKey.CheckedIn to "Checked in", TextKey.CheckInMilestones to "Check-in milestones",
    TextKey.CheckInSummary to "Best streak {best} days • {total} total check-ins",
    TextKey.ConsecutiveDays to "{count} consecutive days", TextKey.TotalCheckIns to "{count} check-ins",
    TextKey.SteadyStartAchievement to "Achievement: Steady Start", TextKey.DiligentTitle to "Title: Diligent",
    TextKey.PersistentFrame to "Persistent frame", TextKey.SeasonHistoryTitle to "Season history",
    TextKey.RankedJourney to "Ranked journey", TextKey.SeasonsPlayed to "{count} seasons played",
    TextKey.SeasonHistoryDescription to "Review your highest tier, peak Elo and rewards from every season.",
    TextKey.LoadingSeasonHistory to "Loading season history…", TextKey.SeasonAchievements to "Achievements by season",
    TextKey.SeasonHistoryRefreshHint to "Pull down to refresh the latest season results.",
    TextKey.NoSeasonHistory to "No season history", TextKey.NoSeasonHistoryDescription to "Complete ranked matches to record your first season.",
    TextKey.Unranked to "Unranked", TextKey.Latest to "LATEST",
    TextKey.SeasonSummaryLine to "Season {season} • {matches} • {tier}", TextKey.HighestTier to "Highest tier",
    TextKey.HighestElo to "Peak Elo", TextKey.FinalElo to "Final Elo", TextKey.FinalRank to "Final rank",
    TextKey.NotRanked to "Not ranked", TextKey.SeasonRewardReceived to "Reward received",
    TextKey.SeasonRewardProcessing to "Season reward is being processed.",
    TextKey.NoPlacementReward to "Fewer than {count} placement matches, so this season has no reward.",
    TextKey.SeasonProgressPlacement to "Placement {played} of {required} matches", TextKey.HighestTierReached to "Highest tier reached",
    TextKey.EloToNextTier to "{count} Elo to reach {tier}", TextKey.HideTierRewards to "Hide tier rewards",
    TextKey.ViewTierRewards to "View tier rewards", TextKey.FinishPlacementHint to "Finish placement to confirm your tier and unlock rewards.",
    TextKey.SeasonPeakElo to "Season peak: {elo} Elo • {tier}", TextKey.HeldReward to "Reward currently held",
    TextKey.ReachedHighestTier to "Highest tier reached", TextKey.PassedTier to "Passed",
    TextKey.TierStartsAtElo to "From {elo} Elo", TextKey.RewardReceiptDescription to "Received {season} reward, {tier} tier",
    TextKey.SeasonRewardAdded to "Added to resources", TextKey.SeasonSummaryTitle to "Season summary",
    TextKey.SeasonSummaryCongratulations to "Congratulations! Your season result and rewards have been recorded.",
    TextKey.Great to "Great", TextKey.SeasonRewardAvatarName to "Season reward", TextKey.AddedToCollection to "Added to Collection",
    TextKey.ExclusiveSeasonFrame to "Exclusive season frame", TextKey.ExclusiveSeasonTitle to "Exclusive season title",
    TextKey.ExclusiveSeasonCosmetic to "Exclusive season cosmetic", TextKey.SeasonEnded to "Season ended",
    TextKey.DaysRemaining to "{days} days left", TextKey.DaysHoursRemaining to "{days}d {hours}h left",
    TextKey.HoursMinutesRemaining to "{hours}h {minutes}m left", TextKey.MinutesRemaining to "{minutes}m left",
    TextKey.AvatarOfPlayer to "Avatar of {player}", TextKey.SeasonalFrame to "Season frame • {tier}",
    TextKey.BronzeFrame to "Bronze frame", TextKey.SilverFrame to "Silver frame", TextKey.GoldFrame to "Gold frame",
    TextKey.PerfectFrame to "Perfect frame", TextKey.PersistentFrameName to "Persistent frame", TextKey.BasicFrame to "Basic frame",
    TextKey.PresenceOffline to "Offline", TextKey.PresenceOnline to "Online", TextKey.PresenceInRoom to "In a room",
    TextKey.PresencePlaying to "In a match", TextKey.WalletBalanceDescription to "{gold} gold, {gems} gems",
    TextKey.RewardGoldDescription to "{count} gold", TextKey.RewardXpDescription to "{count} XP", TextKey.RewardGemsDescription to "{count} gems",
    TextKey.WalletReceivedGold to "received {count} gold", TextKey.WalletUsedGold to "spent {count} gold",
    TextKey.WalletReceivedXp to "received {count} XP", TextKey.WalletUsedXp to "spent {count} XP",
    TextKey.WalletReceivedGems to "received {count} gems", TextKey.WalletUsedGems to "spent {count} gems",
    TextKey.RankBronze to "Bronze", TextKey.RankSilver to "Silver", TextKey.RankGold to "Gold",
    TextKey.RankPlatinum to "Platinum", TextKey.RankDiamond to "Diamond", TextKey.RankMaster to "Master",
    TextKey.RankChallenger to "Challenger", TextKey.NoWalletActivity to "No transactions",
    TextKey.WalletRewardsAppear to "Rewards and purchased items will appear here.",
    TextKey.WalletNoActivityInFilter to "No transactions in this category.", TextKey.WinRate to "Win rate",
    TextKey.AccuracyLabel to "Accuracy", TextKey.RecentFormTen to "Last 10 matches", TextKey.EloChange to "Elo changes",
    TextKey.PointsCount to "{count} points", TextKey.Overview to "Overview", TextKey.Losses to "Losses",
    TextKey.DrawsLabel to "Draws", TextKey.HighScore to "High score", TextKey.CorrectWrongLabel to "Correct / Wrong",
    TextKey.ModeStatistics to "Statistics by mode", TextKey.AchievementsTitle to "Achievements",
    TextKey.NoAchievements to "No achievements unlocked yet.", TextKey.Frames to "Frames",
    TextKey.CurrentSessionDuration to "Current session {duration}",
    TextKey.MissionPlayOne to "Play 1 online match",
    TextKey.MissionCasualTwo to "Play 2 casual matches",
    TextKey.MissionPlayThree to "Play 3 online matches",
    TextKey.MissionWinOne to "Win 1 online match",
    TextKey.MissionRankedTwo to "Play 2 ranked matches",
    TextKey.MissionDailyCorrectHundred to "Find 100 correct numbers",
    TextKey.MissionAccuracyNinety to "Reach 90% accuracy",
    TextKey.MissionDailyPerfectWin to "Win 1 perfect match",
    TextKey.MissionDailyCheckIn to "Check in today",
    TextKey.MissionDonateGoldFiveHundred to "Donate 500 gold",
    TextKey.MissionWeeklyPlayFifteen to "Play 15 matches this week",
    TextKey.MissionWeeklyWinFive to "Win 5 matches this week",
    TextKey.MissionWeeklyRankedWinThree to "Win 3 ranked matches",
    TextKey.MissionWeeklyCorrectFiveHundred to "Find 500 correct numbers",
    TextKey.MissionWeeklyStreakThree to "Reach a 3-win streak",
    TextKey.MissionWeeklyPerfectThree to "Win 3 perfect matches",
    TextKey.MissionWeeklyDonateGoldTwoThousand to "Donate 2,000 gold",
    TextKey.MissionWeeklyDonateGemsFive to "Donate 5 gems",
    TextKey.MissionCorrectHundred to "Find 100 correct numbers this week",
    TextKey.MissionPerfectWin to "Win 1 match without a wrong tap", TextKey.WeekMonday to "Mon", TextKey.WeekTuesday to "Tue",
    TextKey.WeekWednesday to "Wed", TextKey.WeekThursday to "Thu", TextKey.WeekFriday to "Fri",
    TextKey.WeekSaturday to "Sat", TextKey.WeekSunday to "Sun", TextKey.ModeWinRate to "{rate}% wins",
    TextKey.ModeMatchSummary to "{matches} matches • {wins} wins • {losses} losses • {draws} draws",
    TextKey.ScoreAverage to "High {high} • Average {average}", TextKey.LoadingMatch to "Loading match",
    TextKey.MatchDetails to "Match details", TextKey.ReactionLabel to "Reaction", TextKey.NoEloChange to "No change",
    TextKey.MatchTapSummary to "{duration} • {correct} correct • {wrong} wrong",
    TextKey.Previous to "PREVIOUS", TextKey.Next to "NEXT", TextKey.LockedCosmetic to "Locked · {name}{requirement}",
    TextKey.UnlockLevel to "Level {level}", TextKey.UnlockPerfectFrame to "Level 15 + a perfect win",
    TextKey.UnlockPersistentFrame to "100 check-ins", TextKey.UnlockChampionTitle to "Win 10 matches",
    TextKey.UnlockSpeedTitle to "Win and find all 50 numbers in 30 seconds", TextKey.UnlockDiligentTitle to "30-day check-in streak",
    TextKey.UnlockCheckInAvatar to "50 check-ins", TextKey.AchievementWinTenTitle to "Ten victories",
    TextKey.AchievementWinTenDescription to "Win 10 matches", TextKey.AchievementPerfectTitle to "Perfect match",
    TextKey.AchievementPerfectDescription to "Win without a wrong tap", TextKey.AchievementSpeedTitle to "Lightning speed",
    TextKey.AchievementSpeedDescription to "Find all 50 numbers within 30 seconds", TextKey.AchievementCheckInTitle to "Steady start",
    TextKey.AchievementCheckInDescription to "Check in for 7 consecutive days", TextKey.MissionsTitle to "Missions",
    TextKey.ClanMissionSource to "Clan mission", TextKey.CosmeticPurchaseSource to "Item purchase",
    TextKey.TournamentEntrySource to "Tournament entry fee", TextKey.TournamentPrizeSource to "Tournament champion prize",
    TextKey.SeasonRewardSource to "Season reward", TextKey.GemTopUpSource to "Gem top-up", TextKey.WalletAdjustmentSource to "Resource adjustment"
)

internal val vietnameseProfileTexts = englishProfileTexts + mapOf(
    TextKey.ProfileTitle to "Hồ sơ", TextKey.ExternalPlayerTitle to "Người chơi", TextKey.ProfileLoadError to "Chưa tải được hồ sơ. Hãy kiểm tra kết nối và thử lại.",
    TextKey.InviteToClan to "MỜI VÀO BANG", TextKey.EditProfile to "Chỉnh sửa hồ sơ", TextKey.Nickname to "Biệt danh",
    TextKey.Avatar to "Ảnh đại diện", TextKey.UploadImage to "Tải ảnh lên", TextKey.Matches to "Trận", TextKey.Wins to "Thắng",
    TextKey.FeaturedAchievements to "Thành tích nổi bật", TextKey.BestStreak to "Chuỗi tốt nhất", TextKey.AverageReactionShort to "Phản xạ TB",
    TextKey.Activity to "Hoạt động", TextKey.StatisticsAchievements to "Thống kê & thành tích", TextKey.PerformanceMilestones to "Hiệu suất và mốc đã mở",
    TextKey.WalletHistory to "Lịch sử tài sản", TextKey.WalletHistorySubtitle to "Vàng, Gem và XP", TextKey.CheckInHistory to "Lịch sử điểm danh",
    TextKey.CheckInHistorySubtitle to "Lịch điểm danh và các mốc chuyên cần", TextKey.MissionsSubtitle to "Theo dõi và nhận thưởng",
    TextKey.CollectionTitle to "Bộ sưu tập", TextKey.CollectionSubtitle to "Khung và danh hiệu", TextKey.RecentMatches to "Trận gần đây",
    TextKey.MatchHistory to "Lịch sử thi đấu", TextKey.SettingsAccount to "Cài đặt & tài khoản", TextKey.AppSettings to "Cài đặt ứng dụng",
    TextKey.LoginDevices to "Thiết bị đăng nhập", TextKey.AccountSecurity to "Bảo mật tài khoản", TextKey.Copied to "Đã sao chép", TextKey.Rookie to "Tân binh",
    TextKey.TitleValue to "Danh hiệu: {title}", TextKey.PlayerCodeValue to "Mã: {code}", TextKey.CopyPlayerCode to "Sao chép mã người chơi",
    TextKey.ProfilePerformanceHero to "Phong độ của bạn", TextKey.ProfilePerformanceDescription to "Theo dõi tốc độ, độ chính xác và tiến bộ qua từng chế độ.",
    TextKey.WalletFlowHero to "Dòng tài sản", TextKey.WalletFlowDescription to "Mọi lần nhận và sử dụng Vàng, Gem, XP đều được lưu tại đây.",
    TextKey.AttendanceJourney to "Hành trình chuyên cần", TextKey.AttendanceJourneyDescription to "Xem chuỗi ngày, lịch sử và tiến độ các mốc điểm danh.",
    TextKey.TodayMissions to "Nhiệm vụ hôm nay", TextKey.TodayMissionsDescription to "Hoàn thành thử thách, nhận thưởng và nâng cấp tài khoản.",
    TextKey.PersonalCollectionHero to "Dấu ấn của bạn", TextKey.PersonalCollectionDescription to "Trang bị khung và danh hiệu đã mở khóa.",
    TextKey.RecentCompetitionHero to "Lịch sử thi đấu", TextKey.RecentCompetitionDescription to "Xem lại kết quả và hiệu suất chi tiết.",
    TextKey.Received to "Nhận", TextKey.Used to "Đã dùng", TextKey.NoWalletTransactions to "Chưa có giao dịch tài sản.",
    TextKey.NoWalletTransactionsForFilter to "Không có giao dịch phù hợp với bộ lọc.", TextKey.DailyCheckInSource to "Điểm danh hằng ngày",
    TextKey.MissionRewardSource to "Thưởng nhiệm vụ", TextKey.MatchRewardSource to "Thưởng trận đấu", TextKey.ClanRewardSource to "Thưởng bang hội",
    TextKey.ShopPurchaseSource to "Mua tại cửa hàng", TextKey.WalletOtherSource to "Hoạt động khác", TextKey.DailyMissions to "Hằng ngày",
    TextKey.WeeklyMissions to "Hằng tuần", TextKey.NoMissions to "Chưa có nhiệm vụ mới.", TextKey.MissionProgress to "{completed}/{total} hoàn thành",
    TextKey.Claimed to "Đã nhận", TextKey.Claiming to "ĐANG NHẬN", TextKey.ClaimReward to "NHẬN THƯỞNG", TextKey.MissionCompleted to "Hoàn thành",
    TextKey.DifficultyEasy to "Dễ", TextKey.DifficultyNormal to "Vừa", TextKey.DifficultyHard to "Khó", TextKey.DifficultyElite to "Thử thách",
    TextKey.EmptyCollection to "Bộ sưu tập đang trống.", TextKey.Titles to "Danh hiệu", TextKey.Equipping to "Đang trang bị…",
    TextKey.Equipped to "ĐANG TRANG BỊ", TextKey.TapToEquip to "Chạm để trang bị", TextKey.Unlocked to "Đã mở khóa",
    TextKey.UnlockRequirement to "Mở khóa: {requirement}", TextKey.Locked to "Chưa mở khóa", TextKey.RecentFilterAll to "Tất cả",
    TextKey.RecentFilterWins to "Thắng", TextKey.RecentFilterLosses to "Thua", TextKey.NoCompletedMatches to "Chưa có trận đấu hoàn thành.",
    TextKey.NoMatchesForFilter to "Không có trận phù hợp với bộ lọc.", TextKey.LogoutTitle to "Đăng xuất?",
    TextKey.LogoutDescription to "Bạn sẽ quay về màn đăng nhập trên thiết bị này.", TextKey.LogoutAction to "ĐĂNG XUẤT",
    TextKey.AccountSecurityTitle to "BẢO MẬT TÀI KHOẢN", TextKey.AccountSecurityDescription to "Đổi mật khẩu hoặc quản lý việc xóa tài khoản.",
    TextKey.ChangePassword to "Đổi mật khẩu", TextKey.CurrentPassword to "Mật khẩu hiện tại", TextKey.NewPasswordLabel to "Mật khẩu mới",
    TextKey.ConfirmNewPasswordLabel to "Nhập lại mật khẩu mới", TextKey.NewPasswordMustDiffer to "Mật khẩu mới phải khác mật khẩu hiện tại.",
    TextKey.UpdatingPassword to "ĐANG CẬP NHẬT…", TextKey.DangerZone to "Vùng nguy hiểm",
    TextKey.DeleteAccountWarning to "Xóa tài khoản sẽ xóa vĩnh viễn hồ sơ, Elo, lịch sử và thành tích.",
    TextKey.PasswordToConfirm to "Nhập mật khẩu để xác nhận", TextKey.RequestDeleteAccount to "TÔI MUỐN XÓA TÀI KHOẢN",
    TextKey.DeletingAccount to "ĐANG XÓA…", TextKey.LoginDevicesTitle to "THIẾT BỊ ĐĂNG NHẬP",
    TextKey.LoginDevicesDescription to "Kiểm tra và thu hồi những phiên bạn không còn sử dụng.", TextKey.NoActiveSessions to "Không tìm thấy phiên đăng nhập đang hoạt động.",
    TextKey.CurrentDevice to "Thiết bị này", TextKey.LogoutAllDevices to "Đăng xuất tất cả thiết bị", TextKey.LogoutAllDevicesTitle to "ĐĂNG XUẤT TẤT CẢ THIẾT BỊ?",
    TextKey.LogoutAllDevicesDescription to "Tất cả phiên, bao gồm thiết bị này, sẽ bị thu hồi và bạn cần đăng nhập lại.",
    TextKey.RevokeDeviceTitle to "ĐĂNG XUẤT THIẾT BỊ?", TextKey.RevokeCurrentDeviceTitle to "ĐĂNG XUẤT THIẾT BỊ NÀY?",
    TextKey.RevokeCurrentDeviceDescription to "Bạn sẽ quay về màn đăng nhập trên thiết bị hiện tại.",
    TextKey.RevokeOtherDeviceDescription to "Phiên trên {device} sẽ bị thu hồi ngay.", TextKey.UnknownDevice to "Thiết bị không xác định",
    TextKey.JustNow to "vừa xong", TextKey.MinutesAgo to "{count} phút trước", TextKey.HoursAgo to "{count} giờ trước", TextKey.DaysAgo to "{count} ngày trước",
    TextKey.SessionExpiresInDays to "Phiên còn hiệu lực khoảng {count} ngày", TextKey.ActivityTime to "Hoạt động {time}", TextKey.CheckIn to "Điểm danh",
    TextKey.StreakDays to "Chuỗi {count} ngày", TextKey.CheckInCalendarDescription to "Lịch điểm danh · Theo dõi tối đa 12 tháng gần nhất",
    TextKey.PreviousMonth to "Tháng trước", TextKey.NextMonth to "Tháng sau", TextKey.MonthYear to "Tháng {month}/{year}", TextKey.CheckedIn to "Đã điểm danh",
    TextKey.CheckInMilestones to "Mốc điểm danh", TextKey.CheckInSummary to "Chuỗi tốt nhất {best} ngày • Tổng {total} lần",
    TextKey.ConsecutiveDays to "{count} ngày liên tiếp", TextKey.TotalCheckIns to "{count} lần điểm danh",
    TextKey.SteadyStartAchievement to "Thành tích Khởi đầu đều đặn", TextKey.DiligentTitle to "Danh hiệu Chuyên cần", TextKey.PersistentFrame to "Khung Bền bỉ",
    TextKey.SeasonHistoryTitle to "Lịch sử mùa", TextKey.RankedJourney to "Hành trình xếp hạng", TextKey.SeasonsPlayed to "{count} mùa đã thi đấu",
    TextKey.SeasonHistoryDescription to "Xem lại bậc, Elo cao nhất và phần thưởng qua từng mùa.", TextKey.LoadingSeasonHistory to "Đang tải lịch sử mùa…",
    TextKey.SeasonAchievements to "Thành tích qua các mùa", TextKey.SeasonHistoryRefreshHint to "Kéo xuống để cập nhật kết quả mùa mới nhất.",
    TextKey.NoSeasonHistory to "Chưa có lịch sử mùa", TextKey.NoSeasonHistoryDescription to "Hoàn thành trận xếp hạng để lưu dấu mùa giải đầu tiên.",
    TextKey.Unranked to "Chưa phân hạng", TextKey.Latest to "MỚI NHẤT", TextKey.SeasonSummaryLine to "Mùa {season} • {matches} • {tier}",
    TextKey.HighestTier to "Bậc cao nhất", TextKey.HighestElo to "Elo cao nhất", TextKey.FinalElo to "Elo cuối mùa", TextKey.FinalRank to "Hạng cuối mùa",
    TextKey.NotRanked to "Chưa xếp hạng", TextKey.SeasonRewardReceived to "Phần thưởng đã nhận", TextKey.SeasonRewardProcessing to "Phần thưởng mùa đang được xử lý.",
    TextKey.NoPlacementReward to "Chưa đủ {count} trận phân hạng nên mùa này không có thưởng.", TextKey.SeasonProgressPlacement to "Phân hạng {played} trên {required} trận",
    TextKey.HighestTierReached to "Đã đạt bậc cao nhất", TextKey.EloToNextTier to "Còn {count} Elo để lên {tier}", TextKey.HideTierRewards to "Ẩn thưởng các bậc",
    TextKey.ViewTierRewards to "Xem thưởng các bậc", TextKey.FinishPlacementHint to "Hoàn thành phân hạng để chốt bậc và mở mốc thưởng.",
    TextKey.SeasonPeakElo to "Elo cao nhất mùa: {elo} • {tier}", TextKey.HeldReward to "Mốc thưởng đang giữ", TextKey.ReachedHighestTier to "Bậc cao nhất đã đạt",
    TextKey.PassedTier to "Đã vượt qua", TextKey.TierStartsAtElo to "Từ {elo} Elo", TextKey.RewardReceiptDescription to "Đã nhận thưởng {season}, bậc {tier}",
    TextKey.SeasonRewardAdded to "Đã cộng vào tài sản", TextKey.SeasonSummaryTitle to "Tổng kết mùa",
    TextKey.SeasonSummaryCongratulations to "Chúc mừng! Thành tích và phần thưởng mùa của bạn đã được ghi nhận.", TextKey.Great to "Tuyệt vời",
    TextKey.SeasonRewardAvatarName to "Phần thưởng mùa", TextKey.AddedToCollection to "Đã thêm vào Bộ sưu tập",
    TextKey.ExclusiveSeasonFrame to "Khung mùa độc quyền", TextKey.ExclusiveSeasonTitle to "Danh hiệu mùa độc quyền", TextKey.ExclusiveSeasonCosmetic to "Ngoại trang mùa độc quyền",
    TextKey.SeasonEnded to "Mùa đã kết thúc", TextKey.DaysRemaining to "Còn {days} ngày", TextKey.DaysHoursRemaining to "Còn {days} ngày {hours} giờ",
    TextKey.HoursMinutesRemaining to "Còn {hours} giờ {minutes} phút", TextKey.MinutesRemaining to "Còn {minutes} phút",
    TextKey.AvatarOfPlayer to "Ảnh đại diện của {player}", TextKey.SeasonalFrame to "Khung mùa • {tier}", TextKey.BronzeFrame to "Khung Đồng",
    TextKey.SilverFrame to "Khung Bạc", TextKey.GoldFrame to "Khung Vàng", TextKey.PerfectFrame to "Khung Hoàn hảo",
    TextKey.PersistentFrameName to "Khung Bền bỉ", TextKey.BasicFrame to "Khung cơ bản", TextKey.PresenceOffline to "Đang ngoại tuyến",
    TextKey.PresenceOnline to "Đang trực tuyến", TextKey.PresenceInRoom to "Đang trong phòng", TextKey.PresencePlaying to "Đang trong trận",
    TextKey.WalletBalanceDescription to "{gold} vàng, {gems} gem", TextKey.RewardGoldDescription to "{count} vàng",
    TextKey.RewardXpDescription to "{count} XP", TextKey.RewardGemsDescription to "{count} gem", TextKey.WalletReceivedGold to "nhận {count} vàng",
    TextKey.WalletUsedGold to "dùng {count} vàng", TextKey.WalletReceivedXp to "nhận {count} XP", TextKey.WalletUsedXp to "dùng {count} XP",
    TextKey.WalletReceivedGems to "nhận {count} gem", TextKey.WalletUsedGems to "dùng {count} gem", TextKey.RankBronze to "Đồng",
    TextKey.RankSilver to "Bạc", TextKey.RankGold to "Vàng", TextKey.RankPlatinum to "Bạch kim", TextKey.RankDiamond to "Kim cương",
    TextKey.RankMaster to "Cao thủ", TextKey.RankChallenger to "Thách đấu", TextKey.NoWalletActivity to "Chưa có giao dịch",
    TextKey.WalletRewardsAppear to "Phần thưởng và vật phẩm đã mua sẽ xuất hiện tại đây.",
    TextKey.WalletNoActivityInFilter to "Chưa có giao dịch trong mục này.", TextKey.WinRate to "Tỷ lệ thắng",
    TextKey.AccuracyLabel to "Chính xác", TextKey.RecentFormTen to "Phong độ 10 trận gần nhất", TextKey.EloChange to "Biến động Elo",
    TextKey.PointsCount to "{count} điểm", TextKey.Overview to "Tổng quan", TextKey.Losses to "Thua", TextKey.DrawsLabel to "Hòa",
    TextKey.HighScore to "Điểm cao", TextKey.CorrectWrongLabel to "Đúng / Sai", TextKey.ModeStatistics to "Thống kê theo chế độ",
    TextKey.AchievementsTitle to "Thành tích", TextKey.NoAchievements to "Chưa mở khóa thành tích nào.", TextKey.Frames to "Khung",
    TextKey.CurrentSessionDuration to "Phiên hiện tại {duration}",
    TextKey.MissionPlayOne to "Chơi 1 trận online",
    TextKey.MissionCasualTwo to "Chơi 2 trận đấu thường",
    TextKey.MissionPlayThree to "Chơi 3 trận online",
    TextKey.MissionWinOne to "Thắng 1 trận online",
    TextKey.MissionRankedTwo to "Chơi 2 trận đấu hạng",
    TextKey.MissionDailyCorrectHundred to "Chọn đúng 100 số",
    TextKey.MissionAccuracyNinety to "Đạt độ chính xác 90%",
    TextKey.MissionDailyPerfectWin to "Thắng hoàn hảo 1 trận",
    TextKey.MissionDailyCheckIn to "Điểm danh hôm nay",
    TextKey.MissionDonateGoldFiveHundred to "Quyên góp 500 Vàng",
    TextKey.MissionWeeklyPlayFifteen to "Chơi 15 trận trong tuần",
    TextKey.MissionWeeklyWinFive to "Thắng 5 trận trong tuần",
    TextKey.MissionWeeklyRankedWinThree to "Thắng 3 trận đấu hạng",
    TextKey.MissionWeeklyCorrectFiveHundred to "Chọn đúng 500 số",
    TextKey.MissionWeeklyStreakThree to "Đạt chuỗi thắng 3",
    TextKey.MissionWeeklyPerfectThree to "Thắng hoàn hảo 3 trận",
    TextKey.MissionWeeklyDonateGoldTwoThousand to "Quyên góp 2.000 Vàng",
    TextKey.MissionWeeklyDonateGemsFive to "Quyên góp 5 Gem",
    TextKey.MissionCorrectHundred to "Chọn đúng 100 số trong tuần",
    TextKey.MissionPerfectWin to "Thắng 1 trận không bấm sai", TextKey.WeekMonday to "T2", TextKey.WeekTuesday to "T3",
    TextKey.WeekWednesday to "T4", TextKey.WeekThursday to "T5", TextKey.WeekFriday to "T6",
    TextKey.WeekSaturday to "T7", TextKey.WeekSunday to "CN", TextKey.ModeWinRate to "{rate}% thắng",
    TextKey.ModeMatchSummary to "{matches} trận • {wins} thắng • {losses} thua • {draws} hòa",
    TextKey.ScoreAverage to "Điểm cao {high} • Trung bình {average}", TextKey.LoadingMatch to "Đang tải trận đấu",
    TextKey.MatchDetails to "Chi tiết trận", TextKey.ReactionLabel to "Phản xạ", TextKey.NoEloChange to "Không đổi",
    TextKey.MatchTapSummary to "{duration} • Đúng {correct} • Sai {wrong}",
    TextKey.Previous to "TRƯỚC", TextKey.Next to "SAU", TextKey.LockedCosmetic to "Đã khóa · {name}{requirement}",
    TextKey.UnlockLevel to "Cấp {level}", TextKey.UnlockPerfectFrame to "Cấp 15 + thắng không bấm sai",
    TextKey.UnlockPersistentFrame to "Điểm danh 100 lần", TextKey.UnlockChampionTitle to "Thắng 10 trận",
    TextKey.UnlockSpeedTitle to "Thắng và chọn đủ 50 số trong 30 giây", TextKey.UnlockDiligentTitle to "Điểm danh 30 ngày liên tiếp",
    TextKey.UnlockCheckInAvatar to "Điểm danh 50 lần", TextKey.AchievementWinTenTitle to "Mười chiến thắng",
    TextKey.AchievementWinTenDescription to "Thắng 10 trận", TextKey.AchievementPerfectTitle to "Trận hoàn hảo",
    TextKey.AchievementPerfectDescription to "Chiến thắng mà không bấm sai", TextKey.AchievementSpeedTitle to "Tốc độ tia chớp",
    TextKey.AchievementSpeedDescription to "Chọn đủ 50 số trong 30 giây", TextKey.AchievementCheckInTitle to "Khởi đầu đều đặn",
    TextKey.AchievementCheckInDescription to "Điểm danh 7 ngày liên tiếp", TextKey.MissionsTitle to "Nhiệm vụ",
    TextKey.ClanMissionSource to "Nhiệm vụ bang hội", TextKey.CosmeticPurchaseSource to "Mua vật phẩm",
    TextKey.TournamentEntrySource to "Phí tham gia giải đấu", TextKey.TournamentPrizeSource to "Giải thưởng vô địch",
    TextKey.SeasonRewardSource to "Thưởng mùa giải", TextKey.GemTopUpSource to "Nạp Gem", TextKey.WalletAdjustmentSource to "Điều chỉnh tài sản"
)

internal val spanishProfileTexts = englishProfileTexts + mapOf(
    TextKey.ProfileTitle to "Perfil", TextKey.ExternalPlayerTitle to "Jugador", TextKey.Activity to "Actividad",
    TextKey.StatisticsAchievements to "Estadísticas y logros", TextKey.WalletHistory to "Historial de recursos",
    TextKey.CheckInHistory to "Historial de asistencia", TextKey.CollectionTitle to "Colección", TextKey.RecentMatches to "Partidas recientes",
    TextKey.SettingsAccount to "Ajustes y cuenta", TextKey.AppSettings to "Ajustes de la app", TextKey.LoginDevices to "Dispositivos conectados",
    TextKey.AccountSecurity to "Seguridad de la cuenta", TextKey.LogoutTitle to "¿Cerrar sesión?", TextKey.LogoutAction to "CERRAR SESIÓN",
    TextKey.SeasonHistoryTitle to "Historial de temporadas", TextKey.RankBronze to "Bronce", TextKey.RankSilver to "Plata",
    TextKey.RankGold to "Oro", TextKey.RankPlatinum to "Platino", TextKey.RankDiamond to "Diamante", TextKey.RankMaster to "Maestro"
)

internal val brazilianPortugueseProfileTexts = englishProfileTexts + mapOf(
    TextKey.ProfileTitle to "Perfil", TextKey.Activity to "Atividade", TextKey.StatisticsAchievements to "Estatísticas e conquistas",
    TextKey.WalletHistory to "Histórico de recursos", TextKey.RecentFilterAll to "Todos", TextKey.Received to "Recebidos", TextKey.Used to "Usados",
    TextKey.AppSettings to "Configurações do app", TextKey.CheckInHistory to "Histórico de check-in", TextKey.SeasonHistoryTitle to "Histórico de temporadas"
)

internal val frenchProfileTexts = englishProfileTexts + mapOf(
    TextKey.ProfileTitle to "Profil", TextKey.Activity to "Activité", TextKey.StatisticsAchievements to "Statistiques et succès",
    TextKey.WalletHistory to "Historique des ressources", TextKey.CheckInHistory to "Historique des pointages",
    TextKey.AttendanceJourney to "Parcours d’assiduité", TextKey.CollectionTitle to "Collection", TextKey.AppSettings to "Réglages de l’application",
    TextKey.SeasonHistoryTitle to "Historique des saisons", TextKey.RankBronze to "Bronze", TextKey.RankSilver to "Argent",
    TextKey.RankGold to "Or", TextKey.RankPlatinum to "Platine", TextKey.RankDiamond to "Diamant", TextKey.RankMaster to "Maître"
)

internal val germanProfileTexts = englishProfileTexts + mapOf(
    TextKey.ProfileTitle to "Profil", TextKey.Activity to "Aktivität", TextKey.StatisticsAchievements to "Statistiken und Erfolge",
    TextKey.WalletHistory to "Ressourcenverlauf", TextKey.CheckInHistory to "Check-in-Verlauf", TextKey.CollectionTitle to "Sammlung",
    TextKey.AppSettings to "App-Einstellungen", TextKey.LoginDevices to "Angemeldete Geräte", TextKey.AccountSecurity to "Kontosicherheit",
    TextKey.LogoutTitle to "Abmelden?", TextKey.LogoutDescription to "Du kehrst auf diesem Gerät zur Anmeldung zurück.", TextKey.LogoutAction to "ABMELDEN",
    TextKey.SeasonHistoryTitle to "Saisonverlauf", TextKey.RankBronze to "Bronze", TextKey.RankSilver to "Silber",
    TextKey.RankGold to "Gold", TextKey.RankPlatinum to "Platin", TextKey.RankDiamond to "Diamant", TextKey.RankMaster to "Meister"
)

internal val russianProfileTexts = englishProfileTexts + mapOf(
    TextKey.ProfileTitle to "Профиль", TextKey.Activity to "Активность", TextKey.StatisticsAchievements to "Статистика и достижения",
    TextKey.WalletHistory to "История ресурсов", TextKey.CheckInHistory to "История отметок", TextKey.CollectionTitle to "Коллекция",
    TextKey.AppSettings to "Настройки приложения", TextKey.SeasonHistoryTitle to "История сезонов", TextKey.RankedJourney to "Путь в рейтинге",
    TextKey.SeasonAchievements to "Достижения по сезонам", TextKey.Unranked to "Без ранга", TextKey.Latest to "ПОСЛЕДНИЙ",
    TextKey.RankBronze to "Бронза", TextKey.RankSilver to "Серебро", TextKey.RankGold to "Золото",
    TextKey.RankPlatinum to "Платина", TextKey.RankDiamond to "Алмаз", TextKey.RankMaster to "Мастер", TextKey.RankChallenger to "Претендент"
)

internal val simplifiedChineseProfileTexts = englishProfileTexts + mapOf(
    TextKey.ProfileTitle to "个人资料", TextKey.ExternalPlayerTitle to "玩家", TextKey.Activity to "活动", TextKey.StatisticsAchievements to "统计与成就",
    TextKey.WalletHistory to "资源记录", TextKey.CheckInHistory to "签到记录", TextKey.CollectionTitle to "收藏", TextKey.RecentMatches to "最近对局",
    TextKey.AppSettings to "应用设置", TextKey.LoginDevices to "登录设备", TextKey.AccountSecurity to "账户安全", TextKey.SeasonHistoryTitle to "赛季历史",
    TextKey.RankBronze to "青铜", TextKey.RankSilver to "白银", TextKey.RankGold to "黄金", TextKey.RankPlatinum to "铂金",
    TextKey.RankDiamond to "钻石", TextKey.RankMaster to "大师", TextKey.RankChallenger to "王者"
)

internal val japaneseProfileTexts = englishProfileTexts + mapOf(
    TextKey.ProfileTitle to "プロフィール", TextKey.ExternalPlayerTitle to "プレイヤー", TextKey.Activity to "アクティビティ",
    TextKey.StatisticsAchievements to "統計と実績", TextKey.WalletHistory to "資産履歴", TextKey.CheckInHistory to "ログイン履歴",
    TextKey.CollectionTitle to "コレクション", TextKey.RecentMatches to "最近の対戦", TextKey.AppSettings to "アプリ設定",
    TextKey.LoginDevices to "ログイン端末", TextKey.AccountSecurity to "アカウントの安全", TextKey.SeasonHistoryTitle to "シーズン履歴",
    TextKey.RankBronze to "ブロンズ", TextKey.RankSilver to "シルバー", TextKey.RankGold to "ゴールド",
    TextKey.RankPlatinum to "プラチナ", TextKey.RankDiamond to "ダイヤモンド", TextKey.RankMaster to "マスター", TextKey.RankChallenger to "チャレンジャー"
)

internal val koreanProfileTexts = englishProfileTexts + mapOf(
    TextKey.ProfileTitle to "프로필", TextKey.ExternalPlayerTitle to "플레이어", TextKey.Activity to "활동", TextKey.StatisticsAchievements to "통계 및 업적",
    TextKey.WalletHistory to "재화 내역", TextKey.CheckInHistory to "출석 기록", TextKey.CollectionTitle to "컬렉션", TextKey.RecentMatches to "최근 경기",
    TextKey.AppSettings to "앱 설정", TextKey.LoginDevices to "로그인 기기", TextKey.AccountSecurity to "계정 보안", TextKey.SeasonHistoryTitle to "시즌 기록",
    TextKey.RankBronze to "브론즈", TextKey.RankSilver to "실버", TextKey.RankGold to "골드", TextKey.RankPlatinum to "플래티넘",
    TextKey.RankDiamond to "다이아몬드", TextKey.RankMaster to "마스터", TextKey.RankChallenger to "챌린저"
)

internal val indonesianProfileTexts = englishProfileTexts + mapOf(
    TextKey.ProfileTitle to "Profil", TextKey.ExternalPlayerTitle to "Pemain", TextKey.Activity to "Aktivitas",
    TextKey.StatisticsAchievements to "Statistik dan pencapaian", TextKey.WalletHistory to "Riwayat sumber daya",
    TextKey.CheckInHistory to "Riwayat absen", TextKey.CollectionTitle to "Koleksi", TextKey.RecentMatches to "Pertandingan terbaru",
    TextKey.AppSettings to "Pengaturan aplikasi", TextKey.LoginDevices to "Perangkat masuk", TextKey.AccountSecurity to "Keamanan akun",
    TextKey.SeasonHistoryTitle to "Riwayat musim", TextKey.RankBronze to "Perunggu", TextKey.RankSilver to "Perak",
    TextKey.RankGold to "Emas", TextKey.RankPlatinum to "Platinum", TextKey.RankDiamond to "Berlian", TextKey.RankMaster to "Master"
)

internal val thaiProfileTexts = englishProfileTexts + mapOf(
    TextKey.ProfileTitle to "โปรไฟล์", TextKey.ExternalPlayerTitle to "ผู้เล่น", TextKey.Activity to "กิจกรรม",
    TextKey.StatisticsAchievements to "สถิติและความสำเร็จ", TextKey.WalletHistory to "ประวัติทรัพยากร",
    TextKey.CheckInHistory to "ประวัติเช็กอิน", TextKey.CollectionTitle to "คอลเลกชัน", TextKey.RecentMatches to "แมตช์ล่าสุด",
    TextKey.AppSettings to "ตั้งค่าแอป", TextKey.LoginDevices to "อุปกรณ์ที่เข้าสู่ระบบ", TextKey.AccountSecurity to "ความปลอดภัยบัญชี",
    TextKey.SeasonHistoryTitle to "ประวัติฤดูกาล", TextKey.RankBronze to "บรอนซ์", TextKey.RankSilver to "ซิลเวอร์",
    TextKey.RankGold to "โกลด์", TextKey.RankPlatinum to "แพลทินัม", TextKey.RankDiamond to "ไดมอนด์", TextKey.RankMaster to "มาสเตอร์"
)
