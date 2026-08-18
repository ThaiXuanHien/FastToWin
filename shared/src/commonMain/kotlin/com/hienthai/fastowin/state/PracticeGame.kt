package com.hienthai.fastowin.state

import com.hienthai.fastowin.navigation.GameMode

const val PRACTICE_TIME_ATTACK_MILLIS = 60_000L
const val PRACTICE_TIME_BONUS_MILLIS = 30_000L
private const val PRACTICE_SCORE_PER_NUMBER = 10
private const val PRACTICE_CORRECT_BONUS_MILLIS = 2_000L
private const val PRACTICE_WRONG_PENALTY_MILLIS = 3_000L

data class PracticeGameState(
    val mode: GameMode,
    val numbers: List<Int>,
    val currentTarget: Int = 1,
    val score: Int = 0,
    val correctSelections: Int = 0,
    val wrongSelections: Int = 0,
    val startedAtMillis: Long,
    val nowMillis: Long = startedAtMillis,
    val isComplete: Boolean = false,
    val targetOrder: List<Int> = (1..GAME_NUMBER_COUNT).toList(),
    val targetIndex: Int = 0,
    val selectedNumbers: List<Int> = emptyList(),
    val combo: Int = 0,
    val lives: Int = 3,
    val timeAdjustmentMillis: Long = 0L,
    val targetStartedAtMillis: Long = startedAtMillis
) {
    val elapsedMillis: Long
        get() = (nowMillis - startedAtMillis).coerceAtLeast(0L)

    val timeLeftMillis: Long
        get() = when (mode) {
            GameMode.TIME_ATTACK -> (PRACTICE_TIME_ATTACK_MILLIS - elapsedMillis).coerceAtLeast(0L)
            GameMode.TIME_BONUS ->
                (PRACTICE_TIME_BONUS_MILLIS + timeAdjustmentMillis - elapsedMillis).coerceAtLeast(0L)
            GameMode.SPEED_UP -> {
                val allowed = (5_000L - targetIndex * 70L).coerceAtLeast(1_500L)
                (allowed - (nowMillis - targetStartedAtMillis)).coerceAtLeast(0L)
            }
            else -> 0L
        }

    val accuracyPercent: Int
        get() {
            val total = correctSelections + wrongSelections
            return if (total == 0) 0 else correctSelections * 100 / total
        }

    fun select(number: Int, atMillis: Long): PracticeGameState {
        val current = tick(atMillis)
        if (current.isComplete) return current
        if (number != current.currentTarget) {
            val remainingLives = if (mode == GameMode.SURVIVAL) (current.lives - 1).coerceAtLeast(0) else current.lives
            return current.copy(
                wrongSelections = current.wrongSelections + 1,
                combo = 0,
                lives = remainingLives,
                timeAdjustmentMillis = if (mode == GameMode.TIME_BONUS) {
                    current.timeAdjustmentMillis - PRACTICE_WRONG_PENALTY_MILLIS
                } else current.timeAdjustmentMillis,
                isComplete = remainingLives == 0 ||
                    (mode == GameMode.TIME_BONUS && current.timeLeftMillis <= PRACTICE_WRONG_PENALTY_MILLIS)
            )
        }
        val nextIndex = current.targetIndex + 1
        val nextTarget = current.targetOrder.getOrNull(nextIndex) ?: (GAME_NUMBER_COUNT + 1)
        val nextCombo = current.combo + 1
        val multiplier = if (mode == GameMode.COMBO) when {
            nextCombo >= 20 -> 4
            nextCombo >= 10 -> 3
            nextCombo >= 5 -> 2
            else -> 1
        } else 1
        return current.copy(
            currentTarget = nextTarget,
            targetIndex = nextIndex,
            selectedNumbers = current.selectedNumbers + number,
            score = current.score + PRACTICE_SCORE_PER_NUMBER * multiplier,
            correctSelections = current.correctSelections + 1,
            combo = nextCombo,
            timeAdjustmentMillis = if (mode == GameMode.TIME_BONUS) {
                current.timeAdjustmentMillis + PRACTICE_CORRECT_BONUS_MILLIS
            } else current.timeAdjustmentMillis,
            targetStartedAtMillis = if (mode == GameMode.SPEED_UP) atMillis else current.targetStartedAtMillis,
            isComplete = nextIndex >= GAME_NUMBER_COUNT
        )
    }

    fun tick(atMillis: Long): PracticeGameState {
        if (isComplete) return this
        val updated = copy(nowMillis = atMillis.coerceAtLeast(nowMillis))
        return if (updated.mode in setOf(GameMode.TIME_ATTACK, GameMode.TIME_BONUS, GameMode.SPEED_UP) &&
            updated.timeLeftMillis == 0L
        ) {
            updated.copy(isComplete = true)
        } else {
            updated
        }
    }
}

fun createPracticeGame(
    mode: GameMode,
    nowMillis: Long,
    numbers: List<Int> = (1..GAME_NUMBER_COUNT).shuffled()
): PracticeGameState {
    require(numbers.size == GAME_NUMBER_COUNT && numbers.toSet() == (1..GAME_NUMBER_COUNT).toSet()) {
        "Bàn luyện tập phải chứa đủ các số từ 1 đến $GAME_NUMBER_COUNT."
    }
    val targetOrder = if (mode == GameMode.RANDOM_TARGET) numbers.shuffled() else (1..GAME_NUMBER_COUNT).toList()
    return PracticeGameState(
        mode = mode,
        numbers = numbers,
        currentTarget = targetOrder.first(),
        startedAtMillis = nowMillis,
        targetOrder = targetOrder
    )
}
