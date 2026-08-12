package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ServerMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.UUID

class GameEngineTest {
    @Test
    fun `connecting upgraded account clears guest resume token in memory`() = runTest {
        val repository = InMemoryGuestIdentityRepository()
        val engine = GameEngine(identityRepository = repository)
        val guest = engine.connectGuest("Guest", null)

        val account = engine.connectAccount(
            AuthenticatedAccount(UUID.fromString(guest.playerId), "Guest")
        )

        assertEquals(guest.playerId, account.playerId)
        assertEquals(null, account.resumeToken)
    }

    @Test
    fun `guest can resume the same server identity`() = runTest {
        val engine = GameEngine()
        val first = engine.connectGuest("Hiền", null)
        val resumed = engine.connectGuest("Hiền", first.resumeToken)

        assertEquals(first.playerId, resumed.playerId)
        assertEquals(first.resumeToken, resumed.resumeToken)
    }

    @Test
    fun `guest identity survives a game engine restart when repository is persistent`() = runTest {
        val repository = InMemoryGuestIdentityRepository()
        val first = GameEngine(identityRepository = repository).connectGuest("Hiền", null)

        val resumed = GameEngine(identityRepository = repository)
            .connectGuest("Hiền mới", first.resumeToken)

        assertEquals(first.playerId, resumed.playerId)
        assertEquals(first.resumeToken, resumed.resumeToken)
    }

    @Test
    fun `profile request returns safe empty statistics without database`() = runTest {
        val engine = GameEngine()
        val guest = engine.connectGuest("Hiền", null)

        val response = engine.handle(guest.playerId, ClientMessage.GetProfile)
            .map(Delivery::message)
            .filterIsInstance<ServerMessage.ProfileData>()
            .single()

        assertEquals("Hiền", response.profile.displayName)
        assertTrue(response.profile.playerCode.isNotBlank())
        assertEquals(0, response.profile.statistics.totalMatches)
        assertTrue(response.profile.recentMatches.isEmpty())
    }

    @Test
    fun `leaderboard request returns an empty safe response without database`() = runTest {
        val engine = GameEngine()
        val guest = engine.connectGuest("Hiền", null)

        val response = engine.handle(guest.playerId, ClientMessage.GetLeaderboard)
            .map(Delivery::message)
            .filterIsInstance<ServerMessage.LeaderboardData>()
            .single()

        assertTrue(response.leaderboard.topPlayers.isEmpty())
        assertEquals(null, response.leaderboard.currentPlayer)
    }

    @Test
    fun `wrong password cannot join room`() = runTest {
        val fixture = createRoomFixture()
        val deliveries = fixture.engine.handle(
            fixture.guestId,
            ClientMessage.JoinRoom(fixture.roomId, "sai-mat-khau")
        )

        val error = assertIs<ServerMessage.Error>(deliveries.single().message)
        assertEquals("WRONG_PASSWORD", error.code)
    }

    @Test
    fun `two simultaneous selections advance target only once`() = runTest {
        val fixture = createRoomFixture()
        fixture.engine.handle(
            fixture.guestId,
            ClientMessage.JoinRoom(fixture.roomId, PASSWORD)
        )

        val hostResult = async {
            fixture.engine.handle(
                fixture.hostId,
                ClientMessage.SelectNumber(fixture.roomId, 1, "host-request")
            )
        }
        val guestResult = async {
            fixture.engine.handle(
                fixture.guestId,
                ClientMessage.SelectNumber(fixture.roomId, 1, "guest-request")
            )
        }

        val messages = (hostResult.await() + guestResult.await()).map(Delivery::message)
        val accepted = messages.filterIsInstance<ServerMessage.GameStateUpdated>().single()
        val rejected = messages.filterIsInstance<ServerMessage.Error>().single()

        assertEquals(2, accepted.game.currentTarget)
        assertEquals(10, accepted.game.players.sumOf { it.score })
        assertEquals("WRONG_NUMBER", rejected.code)
        assertNotEquals(accepted.selectedByPlayerId, "")
    }

    @Test
    fun `waiting room is hidden while host disconnects and restored on resume`() = runTest {
        var now = 1_000L
        val engine = GameEngine { now }
        val host = engine.connectGuest("Hiền", null)
        engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Phòng reconnect", PASSWORD, ProtocolGameMode.ORDER)
        )

        assertEquals(1, engine.roomList().rooms.size)
        engine.markDisconnected(host.playerId)
        assertTrue(engine.roomList().rooms.isEmpty())

        now += 10_000L
        val resumed = engine.connectGuest("Hiền", host.resumeToken)
        assertEquals(host.playerId, resumed.playerId)
        assertEquals(1, engine.roomList().rooms.size)
        assertEquals("Phòng reconnect", resumed.currentGame?.roomName)
    }

    @Test
    fun `room closes after disconnected player exceeds reconnect grace period`() = runTest {
        var now = 1_000L
        val engine = GameEngine { now }
        val host = engine.connectGuest("Hiền", null)
        val guest = engine.connectGuest("Hiếu", null)
        val created = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Phòng hết hạn", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single()
        engine.handle(guest.playerId, ClientMessage.JoinRoom(created.game.roomId, PASSWORD))

        engine.markDisconnected(host.playerId)
        now += 29_999L
        assertTrue(engine.cleanupExpiredSessions().isEmpty())

        now += 1L
        val cleanupMessages = engine.cleanupExpiredSessions().map(Delivery::message)
        assertEquals(1, cleanupMessages.filterIsInstance<ServerMessage.RoomClosed>().size)

        val result = engine.handle(
            guest.playerId,
            ClientMessage.SelectNumber(created.game.roomId, 1, "after-expiry")
        )
        assertEquals("ROOM_NOT_FOUND", assertIs<ServerMessage.Error>(result.single().message).code)
    }

    @Test
    fun `completed match is persisted once with winner and statistics outcome`() = runTest {
        val savedMatches = mutableListOf<CompletedMatch>()
        val repository = MatchResultRepository { match -> savedMatches += match }
        val engine = GameEngine(matchResultRepository = repository)
        val host = engine.connectGuest("Hiền", null)
        val guest = engine.connectGuest("Hiếu", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Phòng lịch sử", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        engine.handle(guest.playerId, ClientMessage.JoinRoom(room.roomId, PASSWORD))

        val firstWrong = engine.handle(
            host.playerId,
            ClientMessage.SelectNumber(room.roomId, 50, "same-wrong-request")
        )
        val duplicateWrong = engine.handle(
            host.playerId,
            ClientMessage.SelectNumber(room.roomId, 50, "same-wrong-request")
        )
        assertEquals(firstWrong.map(Delivery::message), duplicateWrong.map(Delivery::message))

        repeat(50) { index ->
            engine.handle(
                host.playerId,
                ClientMessage.SelectNumber(room.roomId, index + 1, "finish-$index")
            )
        }
        engine.handle(
            host.playerId,
            ClientMessage.SelectNumber(room.roomId, 50, "finish-49")
        )

        val saved = assertNotNull(savedMatches.singleOrNull())
        assertEquals(room.roomId, saved.matchId)
        assertEquals(host.playerId, saved.winnerPlayerId)
        assertEquals(MatchOutcome.WIN, saved.players.single { it.playerId == host.playerId }.outcome)
        assertEquals(MatchOutcome.LOSS, saved.players.single { it.playerId == guest.playerId }.outcome)
        assertEquals(500, saved.players.single { it.playerId == host.playerId }.score)
        assertEquals(51, saved.events.size)
        assertEquals(50, saved.events.count { it.result == SelectionResult.ACCEPTED })
        assertEquals(1, saved.events.count { it.result == SelectionResult.REJECTED })
        assertEquals(1, saved.events.single { it.result == SelectionResult.REJECTED }.expectedNumber)
    }

    @Test
    fun `server finishes time attack and persists a draw exactly once`() = runTest {
        var now = 10_000L
        val savedMatches = mutableListOf<CompletedMatch>()
        val engine = GameEngine(
            matchResultRepository = MatchResultRepository { savedMatches += it },
            nowMillis = { now }
        )
        val host = engine.connectGuest("Hiền", null)
        val guest = engine.connectGuest("Hiếu", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Phòng 60 giây", PASSWORD, ProtocolGameMode.TIME_ATTACK)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        engine.handle(guest.playerId, ClientMessage.JoinRoom(room.roomId, PASSWORD))

        now += 59_999L
        assertTrue(engine.advanceTimedGames().isEmpty())
        now += 1L
        val finished = engine.advanceTimedGames().map(Delivery::message)
            .filterIsInstance<ServerMessage.GameFinished>()
            .single()

        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.FINISHED, finished.game.phase)
        assertEquals(2, savedMatches.single().players.size)
        assertTrue(savedMatches.single().players.all { it.outcome == MatchOutcome.DRAW })
        assertTrue(engine.advanceTimedGames().isEmpty())
        assertEquals(1, savedMatches.size)
    }

    private suspend fun createRoomFixture(): Fixture {
        val engine = GameEngine()
        val host = engine.connectGuest("Hiền", null)
        val guest = engine.connectGuest("Hiếu", null)
        val deliveries = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Phòng test", PASSWORD, ProtocolGameMode.ORDER)
        )
        val created = deliveries.map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single()
        assertTrue(created.game.numbers.isEmpty())
        return Fixture(engine, host.playerId, guest.playerId, created.game.roomId)
    }

    private data class Fixture(
        val engine: GameEngine,
        val hostId: String,
        val guestId: String,
        val roomId: String
    )

    private companion object {
        const val PASSWORD = "123456"
    }
}
