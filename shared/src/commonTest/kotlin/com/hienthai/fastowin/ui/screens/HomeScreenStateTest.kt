package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.LeaderboardSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `home rank stays unranked when current season has no placement`() {
        val leaderboard = LeaderboardSnapshot(currentPlayer = entry(rank = 4))

        assertNull(leaderboard.homeRank())
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
