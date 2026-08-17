package com.hienthai.fastowin.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressionRulesTest {
    @Test
    fun `perfect game requires a win with a correct selection and no mistakes`() {
        assertTrue(qualifiesForPerfectGame(MatchOutcome.WIN, correctSelections = 26, wrongSelections = 0))
        assertFalse(qualifiesForPerfectGame(MatchOutcome.LOSS, correctSelections = 24, wrongSelections = 0))
        assertFalse(qualifiesForPerfectGame(MatchOutcome.WIN, correctSelections = 26, wrongSelections = 1))
        assertFalse(qualifiesForPerfectGame(MatchOutcome.WIN, correctSelections = 0, wrongSelections = 0))
    }

    @Test
    fun `speed 50 requires winning all 50 selections within 30 seconds`() {
        assertTrue(qualifiesForSpeed50(MatchOutcome.WIN, correctSelections = 50, matchDurationMillis = 30_000))
        assertFalse(qualifiesForSpeed50(MatchOutcome.LOSS, correctSelections = 50, matchDurationMillis = 29_000))
        assertFalse(qualifiesForSpeed50(MatchOutcome.WIN, correctSelections = 49, matchDurationMillis = 29_000))
        assertFalse(qualifiesForSpeed50(MatchOutcome.WIN, correctSelections = 50, matchDurationMillis = 30_001))
    }

    @Test
    fun `frames unlock in increasing difficulty order`() {
        assertTrue("frame_default" in unlockedFrameIds(level = 1, achievementCodes = emptySet()))
        assertFalse("frame_bronze" in unlockedFrameIds(level = 2, achievementCodes = emptySet()))
        assertTrue("frame_bronze" in unlockedFrameIds(level = 3, achievementCodes = emptySet()))
        assertFalse("frame_gold" in unlockedFrameIds(level = 9, achievementCodes = emptySet()))
        assertTrue("frame_gold" in unlockedFrameIds(level = 10, achievementCodes = emptySet()))
        assertFalse("frame_perfect" in unlockedFrameIds(level = 14, achievementCodes = setOf("PERFECT_GAME")))
        assertFalse("frame_perfect" in unlockedFrameIds(level = 15, achievementCodes = emptySet()))
        assertTrue("frame_perfect" in unlockedFrameIds(level = 15, achievementCodes = setOf("PERFECT_GAME")))
        assertFalse("frame_persistent" in unlockedFrameIds(level = 20, achievementCodes = emptySet(), totalDailyCheckIns = 99))
        assertTrue("frame_persistent" in unlockedFrameIds(level = 1, achievementCodes = emptySet(), totalDailyCheckIns = 100))
    }

    @Test
    fun `daily check in title and avatar unlock at their exact milestones`() {
        assertFalse("title_diligent" in unlockedTitleIds(0, emptySet(), bestDailyCheckInStreak = 29))
        assertTrue("title_diligent" in unlockedTitleIds(0, emptySet(), bestDailyCheckInStreak = 30))
        assertTrue(unlockedAvatarIds(49).isEmpty())
        assertTrue("calendar" in unlockedAvatarIds(50))
    }
}
