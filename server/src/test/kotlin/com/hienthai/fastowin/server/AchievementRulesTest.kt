package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MissionDifficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementRulesTest {
    @Test
    fun `catalog contains the exact twenty approved achievements`() {
        assertEquals(
            listOf(
                ExpectedAchievement("FIRST_WIN", 1, MissionDifficulty.EASY, null, "title_first_battle"),
                ExpectedAchievement("WINS_10", 10, MissionDifficulty.NORMAL, "frame_warrior", "title_master"),
                ExpectedAchievement("WINS_50", 50, MissionDifficulty.HARD, "frame_veteran", "title_veteran"),
                ExpectedAchievement("RANKED_WINS_100", 100, MissionDifficulty.ELITE, "frame_diamond", "title_conqueror"),
                ExpectedAchievement("WIN_STREAK_10", 10, MissionDifficulty.ELITE, "frame_unyielding", "title_undefeated"),
                ExpectedAchievement("PERFECT_MATCH_1", 1, MissionDifficulty.EASY, null, "title_one_strike"),
                ExpectedAchievement("PERFECT_MATCHES_10", 10, MissionDifficulty.HARD, "frame_champion", null),
                ExpectedAchievement("ACCURACY_90_TEN", 10, MissionDifficulty.NORMAL, null, "title_divine_eye"),
                ExpectedAchievement("RESPONSE_2500_TEN", 10, MissionDifficulty.HARD, "frame_speed_shadow", "title_golden_reflex"),
                ExpectedAchievement("RESPONSE_1500_TEN", 10, MissionDifficulty.ELITE, "frame_lightning", "title_godspeed"),
                ExpectedAchievement("CHECKIN_STREAK_7", 7, MissionDifficulty.EASY, "frame_wildfire", null),
                ExpectedAchievement("CHECKIN_STREAK_30", 30, MissionDifficulty.HARD, "frame_immortal", null),
                ExpectedAchievement("CHECKINS_50", 50, MissionDifficulty.NORMAL, null, null),
                ExpectedAchievement("CHECKINS_100", 100, MissionDifficulty.ELITE, "frame_supreme", null),
                ExpectedAchievement("PLAYER_LEVEL_30", 30, MissionDifficulty.HARD, "frame_legend", "title_war_god"),
                ExpectedAchievement("CLAN_JOINED", 1, MissionDifficulty.EASY, "frame_glory", null),
                ExpectedAchievement("CLAN_GOLD_10000", 10_000, MissionDifficulty.HARD, "frame_dragon_might", null),
                ExpectedAchievement("CLAN_GEMS_50", 50, MissionDifficulty.ELITE, "frame_emperor", null),
                ExpectedAchievement("CLAN_QUESTS_10", 10, MissionDifficulty.HARD, null, "title_pillar"),
                ExpectedAchievement("CLAN_LEVEL_10", 10, MissionDifficulty.ELITE, "frame_peerless", null)
            ),
            ACHIEVEMENT_DEFINITIONS.map {
                ExpectedAchievement(it.code, it.target, it.difficulty, it.frameId, it.titleId)
            }
        )
        assertEquals(20, ACHIEVEMENT_DEFINITIONS.map { it.code }.distinct().size)
        assertTrue(ACHIEVEMENT_DEFINITIONS.all { it.titleKey.isNotBlank() && it.descriptionKey.isNotBlank() })
    }

    @Test
    fun `difficulty determines the exact achievement reward`() {
        val expected = mapOf(
            MissionDifficulty.EASY to AchievementReward(gold = 100, gems = 0, xp = 20),
            MissionDifficulty.NORMAL to AchievementReward(gold = 250, gems = 0, xp = 50),
            MissionDifficulty.HARD to AchievementReward(gold = 500, gems = 1, xp = 100),
            MissionDifficulty.ELITE to AchievementReward(gold = 1_000, gems = 3, xp = 200)
        )

        ACHIEVEMENT_DEFINITIONS.forEach { definition ->
            assertEquals(expected.getValue(definition.difficulty), definition.reward)
        }
        assertEquals(
            mapOf(
                MissionDifficulty.EASY to 4,
                MissionDifficulty.NORMAL to 3,
                MissionDifficulty.HARD to 7,
                MissionDifficulty.ELITE to 6
            ),
            ACHIEVEMENT_DEFINITIONS.groupingBy { it.difficulty }.eachCount()
        )
    }
}

private data class ExpectedAchievement(
    val code: String,
    val target: Int,
    val difficulty: MissionDifficulty,
    val frameId: String?,
    val titleId: String?
)
