package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MissionDifficulty
import com.hienthai.fastowin.localization.TextKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

internal enum class MissionPeriod { DAILY, WEEKLY }

internal data class MissionDefinition(
    val code: String,
    val title: String,
    val titleKey: TextKey,
    val target: Int,
    val rewardXp: Int,
    val rewardGold: Int,
    val rewardGems: Int,
    val period: MissionPeriod,
    val difficulty: MissionDifficulty
)

internal val MISSION_DEFINITIONS = listOf(
    MissionDefinition(
        "DAILY_PLAY_1", legacyFallback("Chơi 1 trận online"), TextKey.MissionPlayOne, 1, 10, 50, 0,
        MissionPeriod.DAILY, MissionDifficulty.EASY
    ),
    MissionDefinition(
        "DAILY_CASUAL_2", legacyFallback("Chơi 2 trận đấu thường"), TextKey.MissionCasualTwo, 2, 15, 80, 0,
        MissionPeriod.DAILY, MissionDifficulty.EASY
    ),
    MissionDefinition(
        "DAILY_PLAY_3", legacyFallback("Chơi 3 trận online"), TextKey.MissionPlayThree, 3, 20, 100, 0,
        MissionPeriod.DAILY, MissionDifficulty.NORMAL
    ),
    MissionDefinition(
        "DAILY_WIN_1", legacyFallback("Thắng 1 trận online"), TextKey.MissionWinOne, 1, 25, 150, 0,
        MissionPeriod.DAILY, MissionDifficulty.NORMAL
    ),
    MissionDefinition(
        "DAILY_RANKED_2", legacyFallback("Chơi 2 trận đấu hạng"), TextKey.MissionRankedTwo, 2, 25, 120, 0,
        MissionPeriod.DAILY, MissionDifficulty.NORMAL
    ),
    MissionDefinition(
        "DAILY_CORRECT_100", legacyFallback("Chọn đúng 100 số"), TextKey.MissionDailyCorrectHundred, 100, 25, 150, 0,
        MissionPeriod.DAILY, MissionDifficulty.NORMAL
    ),
    MissionDefinition(
        "DAILY_ACCURACY_90", legacyFallback("Đạt độ chính xác 90%"), TextKey.MissionAccuracyNinety, 1, 35, 200, 0,
        MissionPeriod.DAILY, MissionDifficulty.HARD
    ),
    MissionDefinition(
        "DAILY_PERFECT_WIN_1", legacyFallback("Thắng hoàn hảo 1 trận"), TextKey.MissionDailyPerfectWin, 1, 40, 250, 1,
        MissionPeriod.DAILY, MissionDifficulty.ELITE
    ),
    MissionDefinition(
        "DAILY_CHECK_IN", legacyFallback("Điểm danh hôm nay"), TextKey.MissionDailyCheckIn, 1, 10, 50, 0,
        MissionPeriod.DAILY, MissionDifficulty.EASY
    ),
    MissionDefinition(
        "DAILY_DONATE_GOLD_500", legacyFallback("Quyên góp 500 Vàng"), TextKey.MissionDonateGoldFiveHundred, 500, 30, 180, 0,
        MissionPeriod.DAILY, MissionDifficulty.HARD
    ),
    MissionDefinition(
        "WEEKLY_PLAY_15", legacyFallback("Chơi 15 trận trong tuần"), TextKey.MissionWeeklyPlayFifteen, 15, 75, 400, 0,
        MissionPeriod.WEEKLY, MissionDifficulty.NORMAL
    ),
    MissionDefinition(
        "WEEKLY_WIN_5", legacyFallback("Thắng 5 trận trong tuần"), TextKey.MissionWeeklyWinFive, 5, 100, 600, 0,
        MissionPeriod.WEEKLY, MissionDifficulty.HARD
    ),
    MissionDefinition(
        "WEEKLY_RANKED_WIN_3", legacyFallback("Thắng 3 trận đấu hạng"), TextKey.MissionWeeklyRankedWinThree, 3, 120, 700, 1,
        MissionPeriod.WEEKLY, MissionDifficulty.HARD
    ),
    MissionDefinition(
        "WEEKLY_CORRECT_500", legacyFallback("Chọn đúng 500 số"), TextKey.MissionWeeklyCorrectFiveHundred, 500, 90, 500, 0,
        MissionPeriod.WEEKLY, MissionDifficulty.HARD
    ),
    MissionDefinition(
        "WEEKLY_STREAK_3", legacyFallback("Đạt chuỗi thắng 3"), TextKey.MissionWeeklyStreakThree, 3, 140, 800, 1,
        MissionPeriod.WEEKLY, MissionDifficulty.ELITE
    ),
    MissionDefinition(
        "WEEKLY_PERFECT_3", legacyFallback("Thắng hoàn hảo 3 trận"), TextKey.MissionWeeklyPerfectThree, 3, 180, 1_000, 3,
        MissionPeriod.WEEKLY, MissionDifficulty.ELITE
    ),
    MissionDefinition(
        "WEEKLY_DONATE_GOLD_2000", legacyFallback("Quyên góp 2.000 Vàng"), TextKey.MissionWeeklyDonateGoldTwoThousand, 2_000, 100, 600, 0,
        MissionPeriod.WEEKLY, MissionDifficulty.HARD
    ),
    MissionDefinition(
        "WEEKLY_DONATE_GEMS_5", legacyFallback("Quyên góp 5 Gem"), TextKey.MissionWeeklyDonateGemsFive, 5, 120, 700, 2,
        MissionPeriod.WEEKLY, MissionDifficulty.ELITE
    )
)

internal fun missionDefinition(code: String): MissionDefinition? =
    MISSION_DEFINITIONS.firstOrNull { it.code == code }

internal fun missionPeriodStart(definition: MissionDefinition, date: LocalDate): LocalDate =
    when (definition.period) {
        MissionPeriod.DAILY -> date
        MissionPeriod.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

internal fun missionDateAt(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(MISSION_ZONE).toLocalDate()

private val MISSION_ZONE = ZoneId.of("Asia/Bangkok")
