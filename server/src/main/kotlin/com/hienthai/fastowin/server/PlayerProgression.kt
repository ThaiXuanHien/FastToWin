package com.hienthai.fastowin.server

internal data class PlayerLevelProgress(
    val level: Int,
    val currentExperience: Int,
    val nextLevelExperience: Int
)

internal object PlayerProgression {
    const val MAX_LEVEL = 100

    fun levelForExperience(experience: Int): Int = progress(experience).level

    fun progress(experience: Int): PlayerLevelProgress {
        var remaining = experience.coerceAtLeast(0)
        var level = 1
        while (level < MAX_LEVEL) {
            val required = experienceForNextLevel(level)
            if (remaining < required) {
                return PlayerLevelProgress(
                    level = level,
                    currentExperience = remaining,
                    nextLevelExperience = required
                )
            }
            remaining -= required
            level += 1
        }
        val finalRequirement = experienceForNextLevel(MAX_LEVEL - 1)
        return PlayerLevelProgress(
            level = MAX_LEVEL,
            currentExperience = finalRequirement,
            nextLevelExperience = finalRequirement
        )
    }

    fun experienceForNextLevel(currentLevel: Int): Int {
        require(currentLevel in 1 until MAX_LEVEL)
        return 100 + (currentLevel - 1) * 10
    }
}
