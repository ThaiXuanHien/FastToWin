package com.hienthai.fastowin.state

import com.hienthai.fastowin.navigation.GameMode

const val PRACTICE_TIME_ATTACK_MILLIS = 60_000L
private const val PRACTICE_SCORE_PER_NUMBER = 10

data class PracticeGameState(
    val mode: GameMode,
    val numbers: List<Int>,
    val currentTarget: Int = 1,
    val score: Int = 0,
    val correctSelections: Int = 0,
    val wrongSelections: Int = 0,
    val startedAtMillis: Long,
    val nowMillis: Long = startedAtMillis,
    val isComplete: Boolean = false
) {
    val elapsedMillis: Long
        get() = (nowMillis - startedAtMillis).coerceAtLeast(0L)

    val timeLeftMillis: Long
        get() = if (mode == GameMode.TIME_ATTACK) {
            (PRACTICE_TIME_ATTACK_MILLIS - elapsedMillis).coerceAtLeast(0L)
        } else {
            0L
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
            return current.copy(wrongSelections = current.wrongSelections + 1)
        }
        val nextTarget = current.currentTarget + 1
        return current.copy(
            currentTarget = nextTarget,
            score = current.score + PRACTICE_SCORE_PER_NUMBER,
            correctSelections = current.correctSelections + 1,
            isComplete = nextTarget > GAME_NUMBER_COUNT
        )
    }

    fun tick(atMillis: Long): PracticeGameState {
        if (isComplete) return this
        val updated = copy(nowMillis = atMillis.coerceAtLeast(nowMillis))
        return if (updated.mode == GameMode.TIME_ATTACK && updated.timeLeftMillis == 0L) {
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
    return PracticeGameState(mode = mode, numbers = numbers, startedAtMillis = nowMillis)
}
