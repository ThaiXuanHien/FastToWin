package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.LeaderboardSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeScreenStateTest {
    @Test
    fun `home rank follows the current season shown by the default leaderboard tab`() {
        val leaderboard = LeaderboardSnapshot(
            currentPlayer = entry(rank = 4),
            seasonCurrentPlayer = entry(rank = 1)
        )

        assertEquals(1, leaderboard.homeRank())
    }

    @Test
    fun `home rank falls back to all time when current season has no placement`() {
        val leaderboard = LeaderboardSnapshot(currentPlayer = entry(rank = 4))

        assertEquals(4, leaderboard.homeRank())
    }

    private fun entry(rank: Int) = LeaderboardEntrySnapshot(
        rank = rank,
        displayName = "Player",
        playerCode = "PLAYER01",
        wins = 0,
        totalMatches = 0,
        highestScore = 0
    )
}
