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

internal fun missionPeriodStart(definition: MissionDefinition, date: LocalDate): LocalDate =
    when (definition.period) {
        MissionPeriod.DAILY -> date
        MissionPeriod.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

internal fun missionDateAt(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(MISSION_ZONE).toLocalDate()

private val MISSION_ZONE = ZoneId.of("Asia/Bangkok")
