package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MissionDifficulty
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

internal enum class MissionPeriod { DAILY, WEEKLY }

internal data class MissionDefinition(
    val code: String,
    val title: String,
    val target: Int,
    val rewardXp: Int,
    val rewardGold: Int,
    val rewardGems: Int,
    val period: MissionPeriod,
    val difficulty: MissionDifficulty
)

internal val MISSION_DEFINITIONS = listOf(
    MissionDefinition(
        "DAILY_PLAY_3", "Chơi 3 trận hôm nay", 3, 20, 100, 0,
        MissionPeriod.DAILY, MissionDifficulty.EASY
    ),
    MissionDefinition(
        "DAILY_WIN_1", "Thắng 1 trận hôm nay", 1, 25, 150, 0,
        MissionPeriod.DAILY, MissionDifficulty.NORMAL
    ),
    MissionDefinition(
        "WEEKLY_CORRECT_100", "Chọn đúng 100 số trong tuần", 100, 75, 400, 0,
        MissionPeriod.WEEKLY, MissionDifficulty.HARD
    ),
    MissionDefinition(
        "WEEKLY_PERFECT_1", "Thắng 1 trận không bấm sai", 1, 120, 600, 2,
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
