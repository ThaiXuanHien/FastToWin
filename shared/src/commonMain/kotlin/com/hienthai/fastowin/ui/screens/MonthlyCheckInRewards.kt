package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_REWARDS_GEMS
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_REWARDS_GOLD
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_REWARDS_XP
import com.hienthai.fastowin.protocol.DailyCheckInRewardSnapshot
import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import kotlinx.datetime.LocalDate

internal data class CheckInRewardDay(
    val day: Int,
    val reward: DailyCheckInRewardSnapshot?,
    val claimed: Boolean,
    val isToday: Boolean
)

internal fun monthlyCheckInRewards(
    month: ProfileCalendarMonth,
    checkIn: DailyCheckInSnapshot
): List<CheckInRewardDay> {
    val today = checkIn.todayDate?.toProfileCalendarDate() ?: return emptyList()
    val todayEpochDay = LocalDate(today.year, today.month, today.day).toEpochDays()
    val claimedDates = checkIn.historyDates.toSet()
    val cycleLength = DAILY_CHECK_IN_REWARDS_XP.size
    val currentCycleIndex = checkIn.cycleDay.coerceIn(1, cycleLength) - 1
    return profileCalendarCells(month).filterNotNull().map { day ->
        val date = LocalDate(month.year, month.month, day)
        val dateKey = date.toString()
        val offset = date.toEpochDays() - todayEpochDay
        val isToday = offset == 0L
        val storedReward = checkIn.historyRewards[dateKey]
        val reward = when {
            storedReward != null -> storedReward
            isToday -> DailyCheckInRewardSnapshot(
                gold = checkIn.todayRewardGold, xp = checkIn.todayRewardXp, gems = checkIn.todayRewardGems
            )
            offset == 1L -> DailyCheckInRewardSnapshot(
                gold = checkIn.nextRewardGold, xp = checkIn.nextRewardXp, gems = checkIn.nextRewardGems
            )
            offset > 1L -> {
                val cycleIndex = ((currentCycleIndex + offset) % cycleLength).toInt()
                DailyCheckInRewardSnapshot(
                    gold = DAILY_CHECK_IN_REWARDS_GOLD[cycleIndex],
                    xp = DAILY_CHECK_IN_REWARDS_XP[cycleIndex],
                    gems = DAILY_CHECK_IN_REWARDS_GEMS[cycleIndex]
                )
            }
            else -> null
        }
        CheckInRewardDay(
            day = day,
            reward = reward,
            claimed = storedReward != null || dateKey in claimedDates || (isToday && checkIn.claimedToday),
            isToday = isToday
        )
    }
}
