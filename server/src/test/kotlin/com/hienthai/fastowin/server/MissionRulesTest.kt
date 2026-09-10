package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MissionDifficulty
import com.hienthai.fastowin.localization.TextKey
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MissionRulesTest {
    @Test
    fun `mission catalog contains the approved daily and weekly definitions`() {
        assertEquals(
            listOf(
                ExpectedMission("DAILY_PLAY_1", 1, MissionDifficulty.EASY, 10, 50, 0),
                ExpectedMission("DAILY_CASUAL_2", 2, MissionDifficulty.EASY, 15, 80, 0),
                ExpectedMission("DAILY_PLAY_3", 3, MissionDifficulty.NORMAL, 20, 100, 0),
                ExpectedMission("DAILY_WIN_1", 1, MissionDifficulty.NORMAL, 25, 150, 0),
                ExpectedMission("DAILY_RANKED_2", 2, MissionDifficulty.NORMAL, 25, 120, 0),
                ExpectedMission("DAILY_CORRECT_100", 100, MissionDifficulty.NORMAL, 25, 150, 0),
                ExpectedMission("DAILY_ACCURACY_90", 1, MissionDifficulty.HARD, 35, 200, 0),
                ExpectedMission("DAILY_PERFECT_WIN_1", 1, MissionDifficulty.ELITE, 40, 250, 1),
                ExpectedMission("DAILY_CHECK_IN", 1, MissionDifficulty.EASY, 10, 50, 0),
                ExpectedMission("DAILY_DONATE_GOLD_500", 500, MissionDifficulty.HARD, 30, 180, 0)
            ),
            MISSION_DEFINITIONS
                .filter { it.period == MissionPeriod.DAILY }
                .map { it.toExpectation() }
        )
        assertEquals(
            listOf(
                ExpectedMission("WEEKLY_PLAY_15", 15, MissionDifficulty.NORMAL, 75, 400, 0),
                ExpectedMission("WEEKLY_WIN_5", 5, MissionDifficulty.HARD, 100, 600, 0),
                ExpectedMission("WEEKLY_RANKED_WIN_3", 3, MissionDifficulty.HARD, 120, 700, 1),
                ExpectedMission("WEEKLY_CORRECT_500", 500, MissionDifficulty.HARD, 90, 500, 0),
                ExpectedMission("WEEKLY_STREAK_3", 3, MissionDifficulty.ELITE, 140, 800, 1),
                ExpectedMission("WEEKLY_PERFECT_3", 3, MissionDifficulty.ELITE, 180, 1_000, 3),
                ExpectedMission("WEEKLY_DONATE_GOLD_2000", 2_000, MissionDifficulty.HARD, 100, 600, 0),
                ExpectedMission("WEEKLY_DONATE_GEMS_5", 5, MissionDifficulty.ELITE, 120, 700, 2)
            ),
            MISSION_DEFINITIONS
                .filter { it.period == MissionPeriod.WEEKLY }
                .map { it.toExpectation() }
        )
    }

    @Test
    fun `daily and weekly periods reset at their boundaries`() {
        val sunday = LocalDate.of(2026, 8, 23)
        val daily = missionDefinition("DAILY_PLAY_3")!!
        val weekly = missionDefinition("WEEKLY_CORRECT_500")!!

        assertEquals(sunday, missionPeriodStart(daily, sunday))
        assertEquals(LocalDate.of(2026, 8, 17), missionPeriodStart(weekly, sunday))
        assertEquals(LocalDate.of(2026, 8, 24), missionPeriodStart(weekly, sunday.plusDays(1)))
    }

    @Test
    fun `generated season copy carries stable localization templates`() {
        val initial = defaultSeasonName(1)
        val name = defaultSeasonName(3)
        val reward = defaultSeasonRewardDescription()

        assertEquals(TextKey.SeasonInitialName, initial.key)
        assertEquals("Mùa Khởi Đầu", initial.fallback)
        assertEquals(initial, defaultSeasonNameMetadata(1, "Mùa Khởi Đầu"))
        assertEquals(null, defaultSeasonNameMetadata(1, "Creator Cup"))
        assertEquals(TextKey.SeasonDefaultName, name.key)
        assertEquals(mapOf("season" to "3"), name.arguments)
        assertEquals("Mùa 3", name.fallback)
        assertEquals(TextKey.SeasonDefaultRewardDescription, reward.key)
    }

    private fun MissionDefinition.toExpectation() = ExpectedMission(
        code = code,
        target = target,
        difficulty = difficulty,
        rewardXp = rewardXp,
        rewardGold = rewardGold,
        rewardGems = rewardGems
    )

    private data class ExpectedMission(
        val code: String,
        val target: Int,
        val difficulty: MissionDifficulty,
        val rewardXp: Int,
        val rewardGold: Int,
        val rewardGems: Int
    )
}
