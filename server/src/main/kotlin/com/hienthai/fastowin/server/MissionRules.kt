package com.hienthai.fastowin.server

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
    val period: MissionPeriod
)

internal val MISSION_DEFINITIONS = listOf(
    MissionDefinition("DAILY_PLAY_3", "Chơi 3 trận hôm nay", 3, 20, MissionPeriod.DAILY),
    MissionDefinition("DAILY_WIN_1", "Thắng 1 trận hôm nay", 1, 15, MissionPeriod.DAILY),
    MissionDefinition("WEEKLY_CORRECT_100", "Chọn đúng 100 số trong tuần", 100, 50, MissionPeriod.WEEKLY),
    MissionDefinition("WEEKLY_PERFECT_1", "Thắng 1 trận không bấm sai", 1, 75, MissionPeriod.WEEKLY)
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
