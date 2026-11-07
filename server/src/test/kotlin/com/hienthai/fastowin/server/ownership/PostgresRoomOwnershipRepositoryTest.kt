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
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.sql.SQLException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PostgresRoomOwnershipRepositoryTest {
    @Test
    fun `two backends can only claim an expired room once`() = runBlocking {
        fixture { f ->
            val initial = f.createRoom()
            f.expire(initial.fence.roomId)
            assertFalse(f.a.renew(initial.fence))
            assertIs<FencedResult.Stale>(f.a.withFencedRoom(initial.fence, initial.revision, null) { _, _ ->
                error("An expired owner must not reach the mutation")
            })
            val go = CompletableDeferred<Unit>()
            val claims = listOf(f.a to f.instanceA, f.b to f.instanceB).map { (repo, instance) ->
                async(Dispatchers.IO) { go.await(); repo.claim(initial.fence.roomId, instance) }
            }
            go.complete(Unit)
            val winner = claims.mapNotNull { it.await() }.single()
            assertEquals(initial.fence.epoch + 1, winner.fence.epoch)
            assertEquals(initial.revision, winner.revision)
            assertEquals(initial.snapshot.roomName, winner.snapshot.roomName)
            assertEquals(winner.fence, f.b.read(initial.fence.roomId)?.fence)
            assertFalse(f.a.renew(initial.fence))
            assertFalse(f.a.release(initial.fence))
            assertIs<FencedResult.Stale>(f.a.withFencedRoom(initial.fence, initial.revision, null) { _, _ ->
                error("A stale owner must not reach the mutation")
            })
        }
    }

    @Test
    fun `concurrent creates for the same account leave exactly one room`() = runBlocking {
        fixture { f ->
            val socket = f.a.bindSocket(f.host)
            val go = CompletableDeferred<Unit>()
            val attempts = listOf(f.a to f.instanceA, f.b to f.instanceB).map { (repo, instance) ->
                async(Dispatchers.IO) { go.await(); repo.createRoom(instance, socket, f.snapshot()) }
            }
            go.complete(Unit)
            val results = attempts.map { it.await() }
            val winner = results.filterIsInstance<FencedResult.Committed>().single().state
            assertEquals(1, results.count { it is FencedResult.Rejected })
            assertEquals(winner.fence.roomId, f.b.lookupRoom(f.host))
            assertEquals(1, f.count("room_ownership_pilot_rooms"))
        }
    }

    @Test
    fun `replaced socket and old revision are rejected before writing`() = runBlocking {
        fixture { f ->
            val oldSocket = f.a.bindSocket(f.host)
            val initial = assertIs<FencedResult.Committed>(f.a.createRoom(f.instanceA, oldSocket, f.snapshot())).state
            val currentSocket = f.b.bindSocket(f.host)
            assertEquals(oldSocket.generation + 1, currentSocket.generation)
            assertIs<FencedResult.Stale>(f.a.withFencedRoom(initial.fence, initial.revision, oldSocket) { _, _ ->
                error("A replaced socket must not reach the mutation")
            })
            val changed = f.a.withFencedRoom(initial.fence, initial.revision, currentSocket) { _, room ->
                RoomMutation.Change(room.snapshot.copy(roomName = "Updated"))
            }
            assertEquals("Updated", assertIs<FencedResult.Committed>(changed).state.snapshot.roomName)
            assertIs<FencedResult.Stale>(f.a.withFencedRoom(initial.fence, initial.revision, currentSocket) { _, _ ->
                error("An old revision must not reach the mutation")
            })
        }
    }

    @Test
    fun `committed events are readable from another connection and expired events yield a gap`() = runBlocking {
        fixture { f ->
            val initial = f.createRoom()
            val committed = assertIs<FencedResult.Committed>(
                f.a.withFencedRoom(initial.fence, initial.revision, null) { _, room ->
                    RoomMutation.Change(room.snapshot.copy(roomName = "Updated"), events = listOf(
                        RoomOwnershipEvent(setOf(f.host), ServerMessage.RoomList(emptyList()))
                    ))
                }
            ).state
            val batch = assertIs<RoomEventBatch.Contiguous>(f.b.readEvents(initial.fence.roomId, initial.revision, 16))
            assertEquals(committed.revision, batch.revisions.single().revision)
            assertEquals(initial.fence.epoch, batch.revisions.single().ownerEpoch)
            assertEquals(setOf(f.host), batch.revisions.single().events.single().recipients)
            f.ageEvents(initial.fence.roomId)
            assertIs<RoomEventBatch.Gap>(f.b.readEvents(initial.fence.roomId, initial.revision, 16))
        }
    }

    @Test
    fun `failed callback rolls back its writes and the room state`() = runBlocking {
        fixture { f ->
            val initial = f.createRoom()
            val result = f.a.withFencedRoom(initial.fence, initial.revision, null) { connection, _ ->
                connection.createStatement().use { it.executeUpdate("INSERT INTO fixture_probe VALUES (1)") }
                throw SQLException("Fixture simulates an interrupted mutation", "40001")
            }
            assertIs<FencedResult.TemporaryFailure>(result)
            assertEquals(0, f.count("fixture_probe"))
            assertEquals(initial.revision, f.b.read(initial.fence.roomId)?.revision)
        }
    }

    @Test
    fun `second takeover keeps the existing recovery deadline`() = runBlocking {
        fixture { f ->
            val initial = f.createRoom()
            val playing = assertIs<FencedResult.Committed>(
                f.a.withFencedRoom(initial.fence, initial.revision, null) { _, room ->
                    RoomMutation.Change(room.snapshot.copy(phase = RoomPhase.PLAYING))
                }
            ).state
            assertTrue(f.a.release(playing.fence))
            val recovered = assertNotNull(f.b.claim(initial.fence.roomId, f.instanceB))
            assertNotNull(recovered.recoveryDeadline)
            assertTrue(f.b.release(recovered.fence))
            val recoveredAgain = assertNotNull(f.a.claim(initial.fence.roomId, f.instanceA))
            assertEquals(recovered.recoveryDeadline, recoveredAgain.recoveryDeadline)
            assertEquals(playing.revision, recoveredAgain.revision)
        }
    }

    @Test
    fun `closing releases membership but preserves a replay tombstone until its window ends`() = runBlocking {
        fixture { f ->
            val initial = f.createRoom()
            val closed = assertIs<FencedResult.Committed>(
                f.a.withFencedRoom(initial.fence, initial.revision, null) { _, room ->
                    RoomMutation.Change(room.snapshot, close = true)
                }
            ).state
            assertTrue(closed.closed)
            assertEquals(null, f.b.lookupRoom(f.host))
            assertEquals(null, f.b.claim(initial.fence.roomId, f.instanceB))
            assertEquals(0, f.b.purgeExpiredClosedRooms(10))
            assertEquals(closed.revision, f.b.read(initial.fence.roomId)?.revision)
            assertIs<RoomEventBatch.Contiguous>(f.b.readEvents(initial.fence.roomId, initial.revision, 10))
            val newRoom = f.createRoom()
            f.ageClosedRoom(initial.fence.roomId)
            assertEquals(1, f.b.purgeExpiredClosedRooms(10))
            assertEquals(null, f.a.read(initial.fence.roomId))
            assertEquals(newRoom.fence.roomId, f.b.lookupRoom(f.host))
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        if (System.getenv("FASTTOWIN_TEST_ROOM_OWNERSHIP") == "true") requireNotNull(url)
        assumeTrue(url != null, "Isolated PostgreSQL fixture required")
        require(url!!.matches(Regex("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_]+"))) {
            "Room ownership tests require a loopback database and no URL options"
        }
        val schema = "room_pilot_test_" + UUID.randomUUID().toString().replace("-", "")
        fun pool(selectedSchema: String?) = HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
            if (selectedSchema != null) this.schema = selectedSchema
        })
        pool(null).use { control ->
            control.connection.use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
            try {
                Flyway.configure().dataSource(control).schemas(schema).defaultSchema(schema).load().migrate()
                pool(schema).use { first ->
                    pool(schema).use { second ->
                        first.connection.use { c ->
                            c.createStatement().use { it.execute("CREATE TABLE fixture_probe (id integer)") }
                        }
                        block(Fixture(first, PostgresRoomOwnershipRepository(first), PostgresRoomOwnershipRepository(second)))
                    }
                }
            } finally {
                control.connection.use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }

    private class Fixture(
        private val dataSource: HikariDataSource,
        val a: PostgresRoomOwnershipRepository,
        val b: PostgresRoomOwnershipRepository
    ) {
        val instanceA: UUID = UUID.randomUUID()
        val instanceB: UUID = UUID.randomUUID()
        val host: UUID = UUID.randomUUID()

        fun snapshot() = StoredActiveRoom(
            roomId = UUID.randomUUID().toString(),
            roomName = "Private classic room",
            host = StoredActivePlayer(host.toString(), "Host", isAccount = true),
            passwordSalt = byteArrayOf(1),
            passwordHash = byteArrayOf(2),
            gameMode = ProtocolGameMode.ORDER,
            phase = RoomPhase.WAITING
        )

        suspend fun createRoom(): OwnedRoomState =
            assertIs<FencedResult.Committed>(a.createRoom(instanceA, a.bindSocket(host), snapshot())).state

        fun expire(roomId: UUID) = update(
            "UPDATE room_ownership_pilot_rooms SET lease_until = clock_timestamp() WHERE room_id = ?", roomId
        )
        fun ageEvents(roomId: UUID) = update(
            "UPDATE room_ownership_pilot_events SET created_at = clock_timestamp() - interval '3 minutes' WHERE room_id = ?",
            roomId
        )
        fun ageClosedRoom(roomId: UUID) = update(
            "UPDATE room_ownership_pilot_rooms SET closed_at = clock_timestamp() - interval '3 minutes' WHERE room_id = ?",
            roomId
        )
        fun count(table: String): Int = dataSource.connection.use { c ->
            c.createStatement().use { stmt -> stmt.executeQuery("SELECT count(*) FROM $table").use { rows ->
                check(rows.next())
                rows.getInt(1)
            } }
        }

        private fun update(sql: String, roomId: UUID) {
            dataSource.connection.use { c -> c.prepareStatement(sql).use { it.setObject(1, roomId); it.executeUpdate() } }
        }
    }
}
