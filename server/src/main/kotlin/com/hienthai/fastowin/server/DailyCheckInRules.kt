package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_REWARDS_XP
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_REWARDS_GOLD
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_REWARDS_GEMS
import java.time.LocalDate

internal data class DailyCheckInDecision(
    val claimedToday: Boolean,
    val resultingStreak: Int,
    val cycleDay: Int,
    val rewardXp: Int,
    val rewardGold: Int,
    val rewardGems: Int
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
        rewardXp = dailyCheckInXpReward(cycleDay),
        rewardGold = dailyCheckInGoldReward(cycleDay),
        rewardGems = dailyCheckInGemReward(cycleDay)
    )
}

internal fun dailyCheckInXpReward(cycleDay: Int): Int =
    DAILY_CHECK_IN_REWARDS_XP[(cycleDay.coerceIn(1, DAILY_CHECK_IN_REWARDS_XP.size) - 1)]

internal fun dailyCheckInGoldReward(cycleDay: Int): Int =
    DAILY_CHECK_IN_REWARDS_GOLD[(cycleDay.coerceIn(1, DAILY_CHECK_IN_REWARDS_GOLD.size) - 1)]

internal fun dailyCheckInGemReward(cycleDay: Int): Int =
    DAILY_CHECK_IN_REWARDS_GEMS[(cycleDay.coerceIn(1, DAILY_CHECK_IN_REWARDS_GEMS.size) - 1)]

internal fun nextDailyCheckInXpReward(cycleDay: Int): Int =
    dailyCheckInXpReward(nextDailyCheckInCycleDay(cycleDay))

internal fun nextDailyCheckInGoldReward(cycleDay: Int): Int =
    dailyCheckInGoldReward(nextDailyCheckInCycleDay(cycleDay))

internal fun nextDailyCheckInGemReward(cycleDay: Int): Int =
    dailyCheckInGemReward(nextDailyCheckInCycleDay(cycleDay))

// Compatibility names used by the existing rule tests; these refer to XP.
internal fun dailyCheckInReward(cycleDay: Int): Int = dailyCheckInXpReward(cycleDay)
internal fun nextDailyCheckInReward(cycleDay: Int): Int = nextDailyCheckInXpReward(cycleDay)

private fun nextDailyCheckInCycleDay(cycleDay: Int): Int =
    if (cycleDay >= DAILY_CHECK_IN_REWARDS_XP.size) 1 else cycleDay + 1
