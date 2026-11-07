package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.StoredActivePlayer
import com.hienthai.fastowin.server.StoredActiveRoom
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RoomEventDeliveryTest {
    @Test
    fun `wrong selection sends its error only to the actor and drains a lost wakeup`() = runTest {
        val rooms = RoomFeed()
        val wakeups = MutableSharedFlow<UUID>()
        val delivery = RoomEventDelivery(rooms, wakeups)
        val hostMessages = mutableListOf<ServerMessage>()
        val guestMessages = mutableListOf<ServerMessage>()
        var failedErrorDelivery = false
        delivery.register(rooms.host, rooms.applied()) {
            if (it is ServerMessage.Error && !failedErrorDelivery) {
                failedErrorDelivery = true
                throw SQLException("Transient auth check failure")
            }
            hostMessages.add(it)
        }
        delivery.register(rooms.guest, rooms.applied()) { guestMessages.add(it) }
        backgroundScope.launch { delivery.run() }
        runCurrent()

        val update = ServerMessage.GameStateUpdated(rooms.state.snapshot.classicSnapshot(), 22,
            rooms.host.playerId.toString(), false)
        val error = ServerMessage.Error("WRONG_NUMBER", "Wrong number", requestId = "selection")
        rooms.commit(RoomOwnershipEvent(listOf(rooms.host.playerId, rooms.guest.playerId), update),
            RoomOwnershipEvent(listOf(rooms.host.playerId), error))
        delivery.onCommand(rooms.host) { rooms.applied(error) }
        delivery.onCommand(rooms.host) { rooms.applied(error) }
        advanceTimeBy(1_000)
        runCurrent()
        wakeups.emit(rooms.state.fence.roomId)
        runCurrent()

        assertEquals(listOf(update, error), hostMessages)
        assertEquals(listOf<ServerMessage>(update), guestMessages)
    }

    @Test
    fun `removed member only receives the terminal event while the replaced socket receives nothing`() = runTest {
        val rooms = RoomFeed()
        val delivery = RoomEventDelivery(rooms)
        val hostMessages = mutableListOf<ServerMessage>()
        val guestMessages = mutableListOf<ServerMessage>()
        delivery.register(rooms.host, rooms.applied()) { hostMessages.add(it) }
        delivery.register(rooms.guest, rooms.applied()) { guestMessages.add(it) }
        backgroundScope.launch { delivery.run() }
        runCurrent()

        rooms.commit(RoomOwnershipEvent(listOf(rooms.host.playerId, rooms.guest.playerId), rooms.applied().publicMessage))
        val closed = ServerMessage.RoomClosed(rooms.state.fence.roomId.toString(), "Host left")
        rooms.commit(RoomOwnershipEvent(listOf(rooms.host.playerId, rooms.guest.playerId), closed))
        rooms.state = rooms.state.copy(closed = true)
        rooms.currentSockets = setOf(rooms.guest)
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(emptyList(), hostMessages)
        assertEquals(listOf<ServerMessage>(closed), guestMessages)
    }

    @Test
    fun `a replacement between messages fences the rest of a revision`() = runTest {
        val rooms = RoomFeed()
        val delivery = RoomEventDelivery(rooms)
        val messages = mutableListOf<ServerMessage>()
        delivery.register(rooms.host, rooms.applied()) {
            messages.add(it)
            rooms.currentSockets = setOf(rooms.guest)
        }

        val update = rooms.applied().publicMessage
        val error = ServerMessage.Error("WRONG_NUMBER", "Wrong number")
        rooms.commit(RoomOwnershipEvent(listOf(rooms.host.playerId), update),
            RoomOwnershipEvent(listOf(rooms.host.playerId), error))
        delivery.onCommand(rooms.host) { rooms.applied(error) }

        assertEquals(listOf(update), messages)
    }

    @Test
    fun `owner change waits for reattachment and resynchronizes past old events`() = runTest {
        val rooms = RoomFeed()
        val messages = mutableListOf<ServerMessage>()
        var reattachReply: OwnedRoomReply = OwnedRoomReply.Retryable(ServerMessage.Error("ROOM_RECOVERING", "Recovering"))
        var attempts = 0
        val delivery = RoomEventDelivery(rooms, reattach = { attempts++; reattachReply })
        delivery.register(rooms.host, rooms.applied()) { messages.add(it) }
        backgroundScope.launch { delivery.run() }
        runCurrent()

        rooms.commit(RoomOwnershipEvent(listOf(rooms.host.playerId), ServerMessage.Error("STALE", "Stale")))
        rooms.state = rooms.state.copy(
            fence = rooms.state.fence.copy(epoch = 2),
            snapshot = rooms.state.snapshot.copy(roomName = "Recovered")
        )
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(emptyList(), messages)

        reattachReply = rooms.applied()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals("Recovered", assertIs<ServerMessage.RoomUpdated>(messages.single()).game.roomName)
        assertEquals(2, attempts)
    }

    @Test
    fun `a command waits for the new owner snapshot even when its first delivery fails`() = runTest {
        val rooms = RoomFeed()
        val messages = mutableListOf<ServerMessage>()
        val allowSnapshot = CompletableDeferred<Unit>()
        var snapshotStarted = false
        var failSnapshot = true
        val delivery = RoomEventDelivery(rooms, reattach = { rooms.applied() })
        delivery.register(rooms.host, rooms.applied()) { message ->
            if (message is ServerMessage.RoomUpdated) {
                snapshotStarted = true
                allowSnapshot.await()
                if (failSnapshot) {
                    failSnapshot = false
                    throw SQLException("Transient auth check failure")
                }
            }
            messages.add(message)
        }
        backgroundScope.launch { delivery.run() }
        runCurrent()

        rooms.state = rooms.state.copy(fence = rooms.state.fence.copy(epoch = 2))
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(snapshotStarted)

        var commandEntered = false
        val rejected = ServerMessage.Error("INVALID_REQUEST_ID", "Invalid request ID")
        val command = async {
            delivery.onCommand(rooms.host) {
                commandEntered = true
                OwnedRoomReply.Rejected(rejected)
            }
        }
        runCurrent()
        assertFalse(commandEntered)
        allowSnapshot.complete(Unit)
        command.await()

        assertTrue(commandEntered)
        assertIs<ServerMessage.RoomUpdated>(messages.first())
        assertEquals(rejected, messages.last())
    }

    @Test
    fun `a reply from a new epoch gates the next command even if the outbox read fails`() = runTest {
        val rooms = RoomFeed()
        val messages = mutableListOf<ServerMessage>()
        val delivery = RoomEventDelivery(rooms, reattach = { rooms.applied() })
        delivery.register(rooms.host, rooms.applied()) { messages.add(it) }
        delivery.onCommand(rooms.host) { position ->
            assertEquals(1L, position?.epoch)
            rooms.state = rooms.state.copy(fence = rooms.state.fence.copy(epoch = 2))
            rooms.commit()
            rooms.failNextEventRead = true
            rooms.applied()
        }
        assertTrue(messages.isEmpty())

        delivery.onCommand(rooms.host) { position ->
            assertEquals(2L, position?.epoch)
            assertIs<ServerMessage.RoomUpdated>(messages.single())
            OwnedRoomReply.NoRoom
        }
    }

    @Test
    fun `retention gap sends the latest snapshot once`() = runTest {
        val rooms = RoomFeed()
        val messages = mutableListOf<ServerMessage>()
        val delivery = RoomEventDelivery(rooms)
        delivery.register(rooms.host, rooms.applied()) { messages.add(it) }
        backgroundScope.launch { delivery.run() }
        runCurrent()

        rooms.commit()
        rooms.history.clear()
        rooms.state = rooms.state.copy(snapshot = rooms.state.snapshot.copy(roomName = "Latest"))
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals("Latest", assertIs<ServerMessage.RoomUpdated>(messages.single()).game.roomName)
    }

    @Test
    fun `an uncertain command can discover its committed room without replaying history`() = runTest {
        val rooms = RoomFeed()
        val messages = mutableListOf<ServerMessage>()
        val delivery = RoomEventDelivery(rooms, reattach = { rooms.applied() })
        delivery.register(rooms.host, initial = null) { messages.add(it) }
        backgroundScope.launch { delivery.run() }
        runCurrent()

        delivery.onCommand(rooms.host) { OwnedRoomReply.Retryable(ServerMessage.Error("TIMEOUT", "Lost reply")) }
        rooms.commit(RoomOwnershipEvent(listOf(rooms.host.playerId), ServerMessage.Error("OLD", "Older event")))
        rooms.state = rooms.state.copy(snapshot = rooms.state.snapshot.copy(roomName = "Attached"))
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals("TIMEOUT", assertIs<ServerMessage.Error>(messages.first()).code)
        assertEquals("Attached", assertIs<ServerMessage.RoomUpdated>(messages.last()).game.roomName)
        assertEquals(2, messages.size)
    }

    @Test
    fun `PostgreSQL notification wakes the matching room and cancellation releases the connection`() = runBlocking(Dispatchers.IO) {
        val url = System.getenv("TEST_DATABASE_URL")
        if (System.getenv("FASTTOWIN_TEST_ROOM_OWNERSHIP") == "true") requireNotNull(url)
        assumeTrue(url != null, "Isolated PostgreSQL fixture required")
        require(url!!.matches(Regex("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_]+")))
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
            connectionTimeout = 2_000
        }).use { pool ->
            val roomId = UUID.randomUUID()
            val wakeup = async {
                withTimeout(5_000) { postgresRoomEventWakeups(pool).first { it == roomId } }
            }
            while (wakeup.isActive) {
                pool.connection.use { connection ->
                    connection.prepareStatement("SELECT pg_notify('room_ownership_pilot', ?)").use { statement ->
                        statement.setString(1, "$roomId:27")
                        statement.execute()
                    }
                }
                delay(50)
            }
            assertEquals(roomId, wakeup.await())
            assertEquals(0, pool.hikariPoolMXBean.activeConnections)
        }
    }

    private class RoomFeed : RoomOwnershipRepository {
        val host = SocketFence(UUID.randomUUID(), 1)
        val guest = SocketFence(UUID.randomUUID(), 1)
        var currentSockets = setOf(host, guest)
        private val fence = OwnerFence(UUID.randomUUID(), UUID.randomUUID(), 1)
        var state = OwnedRoomState(fence, 1, StoredActiveRoom(
            fence.roomId.toString(), roomName = "Original",
            host = StoredActivePlayer(host.playerId.toString(), "Host", true),
            guest = StoredActivePlayer(guest.playerId.toString(), "Guest", true),
            gameMode = ProtocolGameMode.ORDER, phase = RoomPhase.WAITING
        ), recoveryDeadline = null)
        val history = mutableListOf<RoomEventRevision>()

        fun applied(message: ServerMessage = state.snapshot.publicState()) =
            OwnedRoomReply.Applied(message, fence.roomId, state.fence.epoch, state.revision)

        fun commit(vararg events: RoomOwnershipEvent) {
            state = state.copy(revision = state.revision + 1)
            history.add(RoomEventRevision(state.fence.epoch, state.revision, events.toList()))
        }

        override suspend fun read(roomId: UUID): OwnedRoomState? = state.takeIf { roomId == fence.roomId }
        override suspend fun lookupRoom(playerId: UUID): UUID? =
            fence.roomId.takeIf { !state.closed && playerId.toString() in state.snapshot.activePlayers() }

        override suspend fun isCurrentSocket(socket: SocketFence): Boolean = socket in currentSockets
        var failNextEventRead = false

        override suspend fun readEvents(roomId: UUID, afterRevision: Long, limit: Int): RoomEventBatch {
            if (failNextEventRead) {
                failNextEventRead = false
                throw SQLException("Transient read failure")
            }
            check(roomId == fence.roomId)
            val revisions = history.filter { it.revision > afterRevision }.take(limit)
            if (afterRevision < state.revision && revisions.firstOrNull()?.revision != afterRevision + 1) {
                return RoomEventBatch.Gap
            }
            return RoomEventBatch.Contiguous(revisions)
        }

        override suspend fun claim(roomId: UUID, instanceId: UUID): OwnedRoomState? = error("Unexpected claim")
        override suspend fun expiredRooms(limit: Int): List<UUID> = error("Unexpected expiration scan")
        override suspend fun renew(fence: OwnerFence): Boolean = error("Unexpected renewal")
        override suspend fun release(fence: OwnerFence): Boolean = error("Unexpected release")
        override suspend fun bindSocket(playerId: UUID): SocketFence = error("Unexpected bind")
        override suspend fun purgeExpiredClosedRooms(limit: Int): Int = error("Unexpected purge")
        override suspend fun createRoom(
            instanceId: UUID, socket: SocketFence, initialSnapshot: StoredActiveRoom, events: List<RoomOwnershipEvent>
        ): FencedResult = error("Unexpected creation")
        override suspend fun withFencedRoom(
            fence: OwnerFence, expectedRevision: Long, socket: SocketFence?,
            change: (Connection, OwnedRoomState) -> RoomMutation
        ): FencedResult = error("Unexpected mutation")
    }
}
