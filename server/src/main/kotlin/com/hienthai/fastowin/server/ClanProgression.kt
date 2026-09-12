package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClanDonationCurrency

internal data class ClanLevelProgress(
    val level: Int,
    val currentExperience: Int,
    val nextLevelExperience: Int
)

internal object ClanProgression {
    const val MAX_LEVEL = 50

    fun donationExperience(currency: ClanDonationCurrency, amount: Int): Int? = when (currency) {
        ClanDonationCurrency.GOLD -> {
            if (amount <= 0 || amount % 100 != 0) null else amount / 100 * 10
        }

        ClanDonationCurrency.GEMS -> {
            val experience = amount.toLong() * 10
            if (amount <= 0 || experience > Int.MAX_VALUE) null else experience.toInt()
        }
    }

    fun levelForExperience(experience: Long): Int = progress(experience).level

    fun progress(experience: Long): ClanLevelProgress {
        var remaining = experience.coerceAtLeast(0)
        var level = 1
        while (level < MAX_LEVEL) {
            val required = experienceForNextLevel(level)
            if (remaining < required) {
                return ClanLevelProgress(
                    level = level,
                    currentExperience = remaining.toInt(),
                    nextLevelExperience = required
                )
            }
            remaining -= required
            level += 1
        }
        return ClanLevelProgress(
            level = MAX_LEVEL,
            currentExperience = 0,
            nextLevelExperience = 0
        )
    }

    fun experienceForNextLevel(currentLevel: Int): Int {
        require(currentLevel in 1 until MAX_LEVEL)
        return 1_000 + (currentLevel - 1) * 500
    }
}
