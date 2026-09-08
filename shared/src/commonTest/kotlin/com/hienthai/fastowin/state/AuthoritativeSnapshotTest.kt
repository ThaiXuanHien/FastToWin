package com.hienthai.fastowin.state

import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.PlayerSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RoomPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthoritativeSnapshotTest {
    @Test
    fun `server snapshot replaces every optimistic match field`() {
        val local = GameState(
            numbers = (1..50).toList(),
            currentRoomId = "room-1",
            currentRoomName = "Stale room",
            currentMatchId = "stale-match",
            isMatchStarted = true,
            currentTarget = 49,
            score = 99,
            timeLeftMillis = 1L,
            player = PlayerState(
                id = "player-1",
                name = "Me",
                score = 99,
                wrongSelections = 8,
                selectedNumbers = listOf(1, 2, 9),
                currentTarget = 49
            ),
            opponent = PlayerState(id = "player-2", name = "Stale opponent", selectedNumbers = listOf(8)),
            latestGameSequence = 18
        )
        val server = playingSnapshot(
            sequence = 20,
            player = player(
                id = "player-1",
                name = "Me",
                score = 2,
                wrongSelections = 1,
                currentTarget = 3,
                selectedNumbers = listOf(1, 2),
                timeLeftMillis = 12_000L
            ),
            opponent = player(
                id = "player-2",
                name = "Opponent",
                score = 4,
                wrongSelections = 0,
                currentTarget = 5,
                selectedNumbers = listOf(1, 2, 3, 4)
            )
        )

        val restored = local.applyAuthoritativeSnapshot(server, "player-1")

        assertEquals((1..10).toList(), restored.numbers)
        assertEquals(listOf(1, 2), restored.player.selectedNumbers)
        assertEquals(listOf(1, 2, 3, 4), restored.opponent.selectedNumbers)
        assertEquals(3, restored.currentTarget)
        assertEquals(2, restored.player.score)
        assertEquals(2, restored.score)
        assertEquals(1, restored.player.wrongSelections)
        assertEquals(12_000L, restored.timeLeftMillis)
        assertEquals(20, restored.latestGameSequence)
        assertEquals("server-match", restored.currentMatchId)
        assertEquals("Server room", restored.currentRoomName)
        assertTrue(restored.isMatchStarted)
        assertFalse(restored.isGameOver)
    }

    @Test
    fun `finished server snapshot preserves authoritative result data`() {
        val restored = GameState(
            currentRoomId = "room-1",
            isMatchStarted = true,
            winnerPlayerId = "stale-winner"
        ).applyAuthoritativeSnapshot(
            playingSnapshot(
                phase = RoomPhase.FINISHED,
                sequence = 21,
                player = player("player-1", "Me", score = 3, currentTarget = 9),
                opponent = player("player-2", "Opponent", score = 9, currentTarget = 10),
                winnerPlayerId = "player-2"
            ),
            "player-1"
        )

        assertTrue(restored.isGameOver)
        assertFalse(restored.isMatchStarted)
        assertEquals("player-2", restored.winnerPlayerId)
        assertEquals(21, restored.latestGameSequence)
        assertEquals(9, restored.opponent.score)
    }

    private fun playingSnapshot(
        phase: RoomPhase = RoomPhase.PLAYING,
        sequence: Long,
        player: PlayerSnapshot,
        opponent: PlayerSnapshot,
        winnerPlayerId: String? = null
    ) = GameSnapshot(
        roomId = "room-1",
        matchId = "server-match",
        roomName = "Server room",
        hostId = "player-1",
        gameMode = ProtocolGameMode.ORDER,
        phase = phase,
        players = listOf(player, opponent),
        numbers = (1..10).toList(),
        selectedNumbers = player.selectedNumbers,
        currentTarget = player.currentTarget,
        sequence = sequence,
        winnerPlayerId = winnerPlayerId
    )

    private fun player(
        id: String,
        name: String,
        score: Int = 0,
        wrongSelections: Int = 0,
        currentTarget: Int = 1,
        selectedNumbers: List<Int> = emptyList(),
        timeLeftMillis: Long = 0L
    ) = PlayerSnapshot(
        id = id,
        name = name,
        score = score,
        wrongSelections = wrongSelections,
        currentTarget = currentTarget,
        selectedNumbers = selectedNumbers,
        timeLeftMillis = timeLeftMillis
    )
}
