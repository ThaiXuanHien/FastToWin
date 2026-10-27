package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MissionDifficulty
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal object MissionRotation {
    fun forPeriod(period: MissionPeriod, periodStart: LocalDate): List<MissionDefinition> {
        val normalizedStart = when (period) {
            MissionPeriod.DAILY -> periodStart
            MissionPeriod.WEEKLY -> periodStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        val definitions = MISSION_DEFINITIONS.filter { it.period == period }
        val seed = normalizedStart.toEpochDay()
        return when (period) {
            MissionPeriod.DAILY ->
                rotate(definitions.filterDifficulty(MissionDifficulty.EASY), 1, seed) +
                    rotate(definitions.filterDifficulty(MissionDifficulty.NORMAL), 2, seed + 11) +
                    rotate(
                        definitions.filter {
                            it.difficulty == MissionDifficulty.HARD || it.difficulty == MissionDifficulty.ELITE
                        },
                        1,
                        seed + 23
                    )

            MissionPeriod.WEEKLY ->
                rotate(definitions.filterDifficulty(MissionDifficulty.NORMAL), 1, seed) +
                    rotate(definitions.filterDifficulty(MissionDifficulty.HARD), 2, seed + 11) +
                    rotate(definitions.filterDifficulty(MissionDifficulty.ELITE), 1, seed + 23)
        }
    }

    private fun List<MissionDefinition>.filterDifficulty(difficulty: MissionDifficulty): List<MissionDefinition> =
        filter { it.difficulty == difficulty }

    private fun rotate(
        definitions: List<MissionDefinition>,
        count: Int,
        seed: Long
    ): List<MissionDefinition> {
        require(definitions.size >= count) { "Not enough missions for the requested difficulty slot" }
        val startIndex = Math.floorMod(seed, definitions.size.toLong()).toInt()
        return List(count) { offset -> definitions[(startIndex + offset) % definitions.size] }
    }
}
