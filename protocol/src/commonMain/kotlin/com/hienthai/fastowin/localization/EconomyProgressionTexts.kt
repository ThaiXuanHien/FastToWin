package com.hienthai.fastowin.localization

/**
 * Copy introduced by the economy/progression expansion.
 *
 * This catalog deliberately contains a complete entry for every supported language. Keeping it
 * separate from the older partial catalogs prevents a newly added progression label from silently
 * falling back to English.
 */
internal val economyProgressionTexts: Map<AppLanguage, Map<TextKey, String>> =
    mapOf(
        AppLanguage.ENGLISH to progressionCopy(
            shop = "Gold|Gold Vault|Trade Gems for Gold. Every trade is recorded in your wallet history.|Trade Gems for Gold|Trade {gems} Gems for {gold} Gold?|Not enough Gems.|You received {gold} Gold.|This trade was already completed.|Could not complete the trade.|Gold Bag|Gold Chest|Gold Treasury",
            missions = "Play 1 online match|Play 2 casual matches|Play 3 online matches|Win 1 online match|Play 2 ranked matches|Find 100 correct numbers|Reach 90% accuracy|Win without a mistake|Check in today|Donate 500 Gold|Play 15 matches this week|Win 5 matches this week|Win 3 ranked matches|Find 500 correct numbers|Reach a 3-win streak|Win 3 perfect matches|Donate 2,000 Gold|Donate 5 Gems",
            achievementTitles = "First Battle|Master|Veteran|Conqueror|Undefeated|One Strike|Champion|Divine Eye|Golden Reflex|Godspeed|Wildfire|Immortal|Enduring|Supreme|War God|Glory|Dragon Might|Emperor|Pillar|Peerless",
            achievementDescriptions = "Win your first online match.|Win 10 online matches.|Win 50 online matches.|Win 100 ranked matches.|Reach a 10-win streak.|Win a match without a wrong tap.|Win 10 matches without a wrong tap.|Reach at least 90% accuracy in 10 matches.|Average under 2.5 seconds in 10 matches.|Average under 1.5 seconds in 10 matches.|Check in for 7 days in a row.|Check in for 30 days in a row.|Check in 50 times in total.|Check in 100 times in total.|Reach player level 30.|Join a clan.|Donate 10,000 Gold in total.|Donate 50 Gems in total.|Claim 10 clan mission rewards.|Be in a clan when it reaches level 10.",
            frameNames = "Lightning|Wildfire|Warrior|Veteran|Diamond|Challenger|Glory|Unyielding|Legend|Emperor|Speed Shadow|Champion|Immortal|Dragon Might|Supreme|Peerless",
            titleNames = "First Battle|Godspeed|Undefeated|Veteran|Divine Eye|Pillar|One Strike|Golden Reflex|Master|Conqueror|War God|Speed King",
            challengerUnlock = "Reach the Challenger rank.",
            speedKingUnlock = "Finish a season at rank #1."
        ),
        AppLanguage.VIETNAMESE to progressionCopy(
            shop = "Vàng|Kho Vàng|Đổi Gem lấy Vàng. Mọi giao dịch đều được lưu trong lịch sử tài sản.|Đổi Gem lấy Vàng|Đổi {gems} Gem lấy {gold} Vàng?|Không đủ Gem.|Bạn đã nhận {gold} Vàng.|Giao dịch này đã hoàn tất.|Không thể hoàn tất giao dịch.|Túi Vàng|Rương Vàng|Kho Báu Vàng",
            missions = "Chơi 1 trận online|Chơi 2 trận đấu thường|Chơi 3 trận online|Thắng 1 trận online|Chơi 2 trận đấu hạng|Chọn đúng 100 số|Đạt độ chính xác 90%|Thắng mà không bấm sai|Điểm danh hôm nay|Quyên góp 500 Vàng|Chơi 15 trận trong tuần|Thắng 5 trận trong tuần|Thắng 3 trận đấu hạng|Chọn đúng 500 số|Đạt chuỗi thắng 3|Thắng hoàn hảo 3 trận|Quyên góp 2.000 Vàng|Quyên góp 5 Gem",
            achievementTitles = "Khai Chiến|Cao Thủ|Bách Chiến|Kẻ Chinh Phục|Bất Bại|Nhất Kích|Quán Quân|Mắt Thần|Phản Xạ Vàng|Thần Tốc|Liệt Hỏa|Bất Diệt|Bền Bỉ|Chí Tôn|Chiến Thần|Vinh Quang|Long Uy|Đế Vương|Trụ Cột|Vô Song",
            achievementDescriptions = "Thắng trận online đầu tiên.|Thắng 10 trận online.|Thắng 50 trận online.|Thắng 100 trận đấu hạng.|Đạt chuỗi thắng 10 trận.|Thắng một trận không chọn sai.|Thắng 10 trận không chọn sai.|Đạt ít nhất 90% chính xác trong 10 trận.|Phản ứng trung bình dưới 2,5 giây trong 10 trận.|Phản ứng trung bình dưới 1,5 giây trong 10 trận.|Điểm danh liên tiếp 7 ngày.|Điểm danh liên tiếp 30 ngày.|Điểm danh tổng cộng 50 lần.|Điểm danh tổng cộng 100 lần.|Đạt cấp người chơi 30.|Tham gia một bang.|Quyên góp tổng cộng 10.000 Vàng.|Quyên góp tổng cộng 50 Gem.|Nhận thưởng 10 nhiệm vụ bang.|Thuộc bang khi bang đạt cấp 10.",
            frameNames = "Tia Chớp|Liệt Hỏa|Chiến Binh|Bách Chiến|Kim Cương|Thách Đấu|Vinh Quang|Bất Khuất|Huyền Thoại|Đế Vương|Tốc Ảnh|Quán Quân|Bất Diệt|Long Uy|Chí Tôn|Vô Song",
            titleNames = "Khai Chiến|Thần Tốc|Bất Bại|Bách Chiến|Mắt Thần|Trụ Cột|Nhất Kích|Phản Xạ Vàng|Cao Thủ|Kẻ Chinh Phục|Chiến Thần|Vua Tốc Độ",
            challengerUnlock = "Đạt bậc Thách Đấu.",
            speedKingUnlock = "Kết thúc mùa ở hạng 1."
        ),
    )

private data class ProgressionCopy(
    val shop: String,
    val missions: String,
    val achievementTitles: String,
    val achievementDescriptions: String,
    val frameNames: String,
    val titleNames: String,
    val challengerUnlock: String,
    val speedKingUnlock: String
)

private fun progressionCopy(
    shop: String,
    missions: String,
    achievementTitles: String,
    achievementDescriptions: String,
    frameNames: String,
    titleNames: String,
    challengerUnlock: String,
    speedKingUnlock: String
) = ProgressionCopy(
    shop,
    missions,
    achievementTitles,
    achievementDescriptions,
    frameNames,
    titleNames,
    challengerUnlock,
    speedKingUnlock
).toTextMap()

private fun ProgressionCopy.toTextMap(): Map<TextKey, String> = buildMap {
    putParts(SHOP_KEYS, shop)
    putParts(MISSION_KEYS, missions)
    putParts(ACHIEVEMENT_TITLE_KEYS, achievementTitles)
    putParts(ACHIEVEMENT_DESCRIPTION_KEYS, achievementDescriptions)
    putParts(FRAME_NAME_KEYS, frameNames)
    putParts(TITLE_NAME_KEYS, titleNames)

    val descriptions = ACHIEVEMENT_DESCRIPTION_KEYS.associateWith { key -> getValue(key) }
    FRAME_UNLOCK_KEYS.zip(
        listOf(9, 10, 1, 2, 3, null, 15, 4, 14, 17, 8, 6, 11, 16, 13, 19)
    ).forEach { (key, achievementIndex) ->
        put(key, achievementIndex?.let { descriptions.getValue(ACHIEVEMENT_DESCRIPTION_KEYS[it]) } ?: challengerUnlock)
    }
    TITLE_UNLOCK_KEYS.zip(listOf(0, 9, 4, 2, 7, 18, 5, 8, 1, 3, 14, null))
        .forEach { (key, achievementIndex) ->
            put(key, achievementIndex?.let { descriptions.getValue(ACHIEVEMENT_DESCRIPTION_KEYS[it]) } ?: speedKingUnlock)
        }
}

private fun MutableMap<TextKey, String>.putParts(keys: List<TextKey>, values: String) {
    val parts = values.split('|')
    require(parts.size == keys.size) { "Expected ${keys.size} translations, got ${parts.size}" }
    keys.zip(parts).forEach { (key, value) -> put(key, value) }
}

private val SHOP_KEYS: List<TextKey> get() = listOf(
    TextKey.GoldTab, TextKey.GoldVault, TextKey.GoldVaultDescription,
    TextKey.ExchangeGemsForGold, TextKey.GoldExchangeConfirmation, TextKey.NotEnoughGems,
    TextKey.GoldExchangeGranted, TextKey.GoldExchangeAlreadyGranted, TextKey.GoldExchangeFailed,
    TextKey.GoldOfferBagName, TextKey.GoldOfferChestName, TextKey.GoldOfferVaultName
)

private val MISSION_KEYS: List<TextKey> get() = listOf(
    TextKey.MissionPlayOne, TextKey.MissionCasualTwo, TextKey.MissionPlayThree,
    TextKey.MissionWinOne, TextKey.MissionRankedTwo, TextKey.MissionDailyCorrectHundred,
    TextKey.MissionAccuracyNinety, TextKey.MissionDailyPerfectWin, TextKey.MissionDailyCheckIn,
    TextKey.MissionDonateGoldFiveHundred, TextKey.MissionWeeklyPlayFifteen,
    TextKey.MissionWeeklyWinFive, TextKey.MissionWeeklyRankedWinThree,
    TextKey.MissionWeeklyCorrectFiveHundred, TextKey.MissionWeeklyStreakThree,
    TextKey.MissionWeeklyPerfectThree, TextKey.MissionWeeklyDonateGoldTwoThousand,
    TextKey.MissionWeeklyDonateGemsFive
)

private val ACHIEVEMENT_TITLE_KEYS: List<TextKey> get() = listOf(
    TextKey.AchievementFirstWinTitle, TextKey.AchievementWins10Title,
    TextKey.AchievementWins50Title, TextKey.AchievementRankedWins100Title,
    TextKey.AchievementWinStreak10Title, TextKey.AchievementPerfectMatch1Title,
    TextKey.AchievementPerfectMatches10Title, TextKey.AchievementAccuracy90TenTitle,
    TextKey.AchievementResponse2500TenTitle, TextKey.AchievementResponse1500TenTitle,
    TextKey.AchievementCheckinStreak7Title, TextKey.AchievementCheckinStreak30Title,
    TextKey.AchievementCheckins50Title, TextKey.AchievementCheckins100Title,
    TextKey.AchievementPlayerLevel30Title, TextKey.AchievementClanJoinedTitle,
    TextKey.AchievementClanGold10000Title, TextKey.AchievementClanGems50Title,
    TextKey.AchievementClanQuests10Title, TextKey.AchievementClanLevel10Title
)

private val ACHIEVEMENT_DESCRIPTION_KEYS: List<TextKey> get() = listOf(
    TextKey.AchievementFirstWinDescription, TextKey.AchievementWins10Description,
    TextKey.AchievementWins50Description, TextKey.AchievementRankedWins100Description,
    TextKey.AchievementWinStreak10Description, TextKey.AchievementPerfectMatch1Description,
    TextKey.AchievementPerfectMatches10Description, TextKey.AchievementAccuracy90TenDescription,
    TextKey.AchievementResponse2500TenDescription, TextKey.AchievementResponse1500TenDescription,
    TextKey.AchievementCheckinStreak7Description, TextKey.AchievementCheckinStreak30Description,
    TextKey.AchievementCheckins50Description, TextKey.AchievementCheckins100Description,
    TextKey.AchievementPlayerLevel30Description, TextKey.AchievementClanJoinedDescription,
    TextKey.AchievementClanGold10000Description, TextKey.AchievementClanGems50Description,
    TextKey.AchievementClanQuests10Description, TextKey.AchievementClanLevel10Description
)

private val FRAME_NAME_KEYS: List<TextKey> get() = listOf(
    TextKey.FrameLightningName, TextKey.FrameWildfireName, TextKey.FrameWarriorName,
    TextKey.FrameVeteranName, TextKey.FrameDiamondName, TextKey.FrameChallengerName,
    TextKey.FrameGloryName, TextKey.FrameUnyieldingName, TextKey.FrameLegendName,
    TextKey.FrameEmperorName, TextKey.FrameSpeedShadowName, TextKey.FrameChampionName,
    TextKey.FrameImmortalName, TextKey.FrameDragonMightName, TextKey.FrameSupremeName,
    TextKey.FramePeerlessName
)

private val FRAME_UNLOCK_KEYS: List<TextKey> get() = listOf(
    TextKey.FrameLightningUnlockDescription, TextKey.FrameWildfireUnlockDescription,
    TextKey.FrameWarriorUnlockDescription, TextKey.FrameVeteranUnlockDescription,
    TextKey.FrameDiamondUnlockDescription, TextKey.FrameChallengerUnlockDescription,
    TextKey.FrameGloryUnlockDescription, TextKey.FrameUnyieldingUnlockDescription,
    TextKey.FrameLegendUnlockDescription, TextKey.FrameEmperorUnlockDescription,
    TextKey.FrameSpeedShadowUnlockDescription, TextKey.FrameChampionUnlockDescription,
    TextKey.FrameImmortalUnlockDescription, TextKey.FrameDragonMightUnlockDescription,
    TextKey.FrameSupremeUnlockDescription, TextKey.FramePeerlessUnlockDescription
)

private val TITLE_NAME_KEYS: List<TextKey> get() = listOf(
    TextKey.TitleFirstBattleName, TextKey.TitleGodspeedName, TextKey.TitleUndefeatedName,
    TextKey.TitleVeteranName, TextKey.TitleDivineEyeName, TextKey.TitlePillarName,
    TextKey.TitleOneStrikeName, TextKey.TitleGoldenReflexName, TextKey.TitleMasterName,
    TextKey.TitleConquerorName, TextKey.TitleWarGodName, TextKey.TitleSpeedKingName
)

private val TITLE_UNLOCK_KEYS: List<TextKey> get() = listOf(
    TextKey.TitleFirstBattleUnlockDescription, TextKey.TitleGodspeedUnlockDescription,
    TextKey.TitleUndefeatedUnlockDescription, TextKey.TitleVeteranUnlockDescription,
    TextKey.TitleDivineEyeUnlockDescription, TextKey.TitlePillarUnlockDescription,
    TextKey.TitleOneStrikeUnlockDescription, TextKey.TitleGoldenReflexUnlockDescription,
    TextKey.TitleMasterUnlockDescription, TextKey.TitleConquerorUnlockDescription,
    TextKey.TitleWarGodUnlockDescription, TextKey.TitleSpeedKingUnlockDescription
)
