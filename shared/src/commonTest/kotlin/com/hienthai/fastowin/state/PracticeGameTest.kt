package com.hienthai.fastowin.state

import com.hienthai.fastowin.navigation.GameMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeGameTest {
    private val orderedNumbers = (1..GAME_NUMBER_COUNT).toList()

    @Test
    fun correctAndWrongSelectionsUpdateLocalStatistics() {
        val initial = createPracticeGame(GameMode.ORDER, nowMillis = 1_000, numbers = orderedNumbers)

        val wrong = initial.select(number = 2, atMillis = 1_100)
        val correct = wrong.select(number = 1, atMillis = 1_200)

        assertEquals(1, wrong.wrongSelections)
        assertEquals(2, correct.currentTarget)
        assertEquals(10, correct.score)
        assertEquals(1, correct.correctSelections)
        assertEquals(50, correct.accuracyPercent)
        assertFalse(correct.isComplete)
    }

    @Test
    fun orderPracticeCompletesAfterNumberFifty() {
        var state = createPracticeGame(GameMode.ORDER, nowMillis = 0, numbers = orderedNumbers)
        for (number in orderedNumbers) state = state.select(number, atMillis = number * 100L)

        assertTrue(state.isComplete)
        assertEquals(51, state.currentTarget)
        assertEquals(500, state.score)
    }

    @Test
    fun timeAttackStopsAtSixtySeconds() {
        val initial = createPracticeGame(GameMode.TIME_ATTACK, nowMillis = 5_000, numbers = orderedNumbers)

        val finished = initial.tick(65_000)

        assertTrue(finished.isComplete)
        assertEquals(0, finished.timeLeftMillis)
        assertEquals(60_000, finished.elapsedMillis)
    }

    @Test
    fun randomModeFollowsGeneratedTargetsInsteadOfNumericOrder() {
        val initial = createPracticeGame(GameMode.RANDOM_TARGET, nowMillis = 0, numbers = orderedNumbers)
        val firstTarget = initial.currentTarget

        val updated = initial.select(firstTarget, atMillis = 100)

        assertEquals(listOf(firstTarget), updated.selectedNumbers)
        assertEquals(initial.targetOrder[1], updated.currentTarget)
    }

    @Test
    fun timeBonusAddsForCorrectAndSubtractsForWrongSelection() {
        val initial = createPracticeGame(GameMode.TIME_BONUS, nowMillis = 0, numbers = orderedNumbers)
        val correct = initial.select(1, atMillis = 1_000)
        val wrong = correct.select(1, atMillis = 1_100)

        assertEquals(31_000, correct.timeLeftMillis)
        assertEquals(27_900, wrong.timeLeftMillis)
    }

    @Test
    fun survivalEndsOnThirdWrongSelection() {
        var state = createPracticeGame(GameMode.SURVIVAL, nowMillis = 0, numbers = orderedNumbers)
        repeat(3) { state = state.select(2, atMillis = it * 100L) }

        assertEquals(0, state.lives)
        assertTrue(state.isComplete)
    }

    @Test
    fun comboRewardsAFiveHitStreakAndResetsAfterAMiss() {
        var state = createPracticeGame(GameMode.COMBO, nowMillis = 0, numbers = orderedNumbers)
        repeat(5) { index -> state = state.select(index + 1, atMillis = index * 100L) }

        assertEquals(5, state.combo)
        assertEquals(60, state.score)

        state = state.select(1, atMillis = 600)
        assertEquals(0, state.combo)
    }

    @Test
    fun speedUpStopsWhenCurrentTargetDeadlineExpires() {
        val initial = createPracticeGame(GameMode.SPEED_UP, nowMillis = 0, numbers = orderedNumbers)

        val finished = initial.tick(5_001)

        assertTrue(finished.isComplete)
        assertEquals(0, finished.timeLeftMillis)
    }
}
