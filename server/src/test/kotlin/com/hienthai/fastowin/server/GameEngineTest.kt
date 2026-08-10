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
import kotlin.test.assertTrue

class GameEngineTest {
    @Test
    fun `guest can resume the same server identity`() = runTest {
        val engine = GameEngine()
        val first = engine.connectGuest("Hiền", null)
        val resumed = engine.connectGuest("Hiền", first.resumeToken)

        assertEquals(first.playerId, resumed.playerId)
        assertEquals(first.resumeToken, resumed.resumeToken)
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
