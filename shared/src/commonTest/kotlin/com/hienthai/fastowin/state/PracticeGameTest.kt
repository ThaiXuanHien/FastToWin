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
}
