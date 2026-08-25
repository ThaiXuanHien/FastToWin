package com.hienthai.fastowin.state

import com.hienthai.fastowin.navigation.GameMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import com.hienthai.fastowin.ui.screens.buildChallengeShareText
import com.hienthai.fastowin.ui.screens.decodePracticeGameState
import com.hienthai.fastowin.ui.screens.encodePracticeGameState

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

    @Test
    fun challengeCodeRestoresTheSameModeBoardAndTargets() {
        val challenge = createPracticeChallenge(GameMode.RANDOM_TARGET, seed = 0x12345678)

        val restored = assertNotNull(parsePracticeChallenge(challenge.code.lowercase()))
        val first = createPracticeGame(challenge.mode, nowMillis = 0, challenge = challenge)
        val second = createPracticeGame(restored.mode, nowMillis = 5_000, challenge = restored)

        assertEquals("FTW-NN-12345678-BE", challenge.code)
        assertEquals(challenge, restored)
        assertEquals(first.numbers, second.numbers)
        assertEquals(first.targetOrder, second.targetOrder)
        assertEquals(challenge.code, second.challengeCode)
    }

    @Test
    fun challengeCodeRejectsTypingErrors() {
        val challenge = createPracticeChallenge(GameMode.ORDER, seed = 1234)

        assertNull(parsePracticeChallenge(challenge.code.dropLast(1) + "0"))
        assertNull(parsePracticeChallenge("FTW-XX-000004D2-00"))
        assertNull(parsePracticeChallenge("không hợp lệ"))
    }

    @Test
    fun sharedChallengeIncludesCodeModeAndResult() {
        val challenge = createPracticeChallenge(GameMode.ORDER, seed = 1234)
        val text = buildChallengeShareText(
            mode = GameMode.ORDER,
            code = challenge.code,
            score = 500,
            elapsedMillis = 42_300
        )

        assertTrue(text.contains("Cổ điển"))
        assertTrue(text.contains("500 điểm"))
        assertTrue(text.contains("00:42.3"))
        assertTrue(text.contains(challenge.code))
        assertTrue(text.contains("fasttowin://challenge/${challenge.code}"))
        assertTrue(text.contains("Luyện tập offline"))
    }

    @Test
    fun savedPracticeProgressRestoresAgainstTheSameChallenge() {
        val challenge = createPracticeChallenge(GameMode.COMBO, seed = 0x13572468)
        var state = createPracticeGame(challenge.mode, nowMillis = 1_000, challenge = challenge)
        repeat(5) { index ->
            state = state.select(state.currentTarget, atMillis = 1_100L + index * 100L)
        }
        state = state.select(number = state.selectedNumbers.first(), atMillis = 1_700)
        state = state.select(number = state.currentTarget, atMillis = 1_800)

        val restored = assertNotNull(decodePracticeGameState(encodePracticeGameState(state), challenge))

        assertEquals(state, restored)
        assertEquals(6, restored.correctSelections)
        assertEquals(1, restored.wrongSelections)
        assertEquals(1, restored.combo)
    }

    @Test
    fun savedPracticeProgressRejectsAnotherBoardOrCorruptPayload() {
        val challenge = createPracticeChallenge(GameMode.RANDOM_TARGET, seed = 0x12345678)
        val anotherChallenge = createPracticeChallenge(GameMode.RANDOM_TARGET, seed = 0x76543210)
        val state = createPracticeGame(challenge.mode, nowMillis = 1_000, challenge = challenge)
            .select(number = challenge.targetOrder.first(), atMillis = 1_200)
        val encoded = encodePracticeGameState(state)

        assertNull(decodePracticeGameState(encoded, anotherChallenge))
        assertNull(decodePracticeGameState(encoded.replaceFirst("|10|", "|-10|"), challenge))
        assertNull(decodePracticeGameState("invalid", challenge))
    }
}
