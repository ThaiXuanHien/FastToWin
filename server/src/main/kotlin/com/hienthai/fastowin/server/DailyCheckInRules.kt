package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_REWARDS_XP
import java.time.LocalDate

internal data class DailyCheckInDecision(
    val claimedToday: Boolean,
    val resultingStreak: Int,
    val cycleDay: Int,
    val rewardXp: Int
)

internal fun dailyCheckInDecision(
    currentStreak: Int,
    lastCheckInDate: LocalDate?,
    today: LocalDate
): DailyCheckInDecision {
    val safeStreak = currentStreak.coerceAtLeast(0)
    val claimedToday = lastCheckInDate == today
    val resultingStreak = when {
        claimedToday -> safeStreak.coerceAtLeast(1)
        lastCheckInDate == today.minusDays(1) -> safeStreak + 1
        else -> 1
    }
    val cycleDay = ((resultingStreak - 1) % DAILY_CHECK_IN_REWARDS_XP.size) + 1
    return DailyCheckInDecision(
        claimedToday = claimedToday,
        resultingStreak = resultingStreak,
        cycleDay = cycleDay,
        rewardXp = dailyCheckInReward(cycleDay)
    )
}

internal fun dailyCheckInReward(cycleDay: Int): Int =
    DAILY_CHECK_IN_REWARDS_XP[(cycleDay.coerceIn(1, DAILY_CHECK_IN_REWARDS_XP.size) - 1)]

internal fun nextDailyCheckInReward(cycleDay: Int): Int =
    dailyCheckInReward(if (cycleDay >= DAILY_CHECK_IN_REWARDS_XP.size) 1 else cycleDay + 1)
