package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.server.AuthResult
import com.hienthai.fastowin.server.AuthenticatedAccount
import com.hienthai.fastowin.server.AuthenticationService
import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.PostgresAuthRepository
import com.hienthai.fastowin.server.StoredActivePlayer
import com.hienthai.fastowin.server.gameModule
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.URI
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource
import kotlin.time.measureTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoomOwnershipReadinessTest {
    @Test
    fun `readiness follows worker and PostgreSQL while liveness stays available`() = runBlocking {
        fixture { f ->
            val source = SwitchableDataSource(f.source)
            val pilot = f.pilot(source)
            assertFalse(pilot.isReady())
            testApplication {
                application { gameModule(roomOwnershipPilot = pilot) }
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
                withTimeout(8_000) {
                    while (client.get("/ready").status != HttpStatusCode.OK) delay(25)
                }

                source.unavailable.set(true)
                assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ready").status)
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
                source.unavailable.set(false)
                withTimeout(8_000) {
                    while (client.get("/ready").status != HttpStatusCode.OK) delay(25)
                }
                assertTrue(pilot.beginDrain())
                assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ready").status)
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
            }
        }
    }

    @Test
    fun `drain waits for admitted create then releases its durable fence before closing sockets`() = runBlocking {
        fixture { f ->
            val entered = CompletableDeferred<Unit>()
            val finishCreate = CompletableDeferred<Unit>()
            val pilot = f.pilot { id ->
                entered.complete(Unit)
                finishCreate.await()
                f.players[id]
            }
            val rooms = PostgresRoomOwnershipRepository(f.source)
            val firstId = f.players.keys.first()
            val connecting = assertNotNull(pilot.onConnected(AuthenticatedAccount(firstId, f.players.getValue(firstId).displayName)))
            val host = connecting.socket
            val other = rooms.bindSocket(f.players.keys.last())
            val close = CompletableDeferred<OwnedRoomState>()
            pilot.delivery.register(host, null, onDrain = {
                val roomId = assertNotNull(rooms.lookupRoom(host.playerId))
                close.complete(assertNotNull(rooms.claim(roomId, UUID.randomUUID())))
            }) {}

            val command = async { pilot.onMessage(host, CREATE) }
            entered.await()
            val draining = async { pilot.beginDrain() }
            withTimeout(3_000) { while (pilot.isAccepting()) delay(10) }
            assertFalse(pilot.isReady())
            assertEquals("ROOM_DRAINING", assertIs<OwnedRoomReply.Retryable>(pilot.onMessage(other, CREATE)).publicMessage.code)
            assertFalse(draining.isCompleted)
            assertFalse(close.isCompleted)

            finishCreate.complete(Unit)
            val created = assertIs<OwnedRoomReply.Applied>(command.await())
            assertFalse(draining.isCompleted)
            connecting.close()
            assertTrue(draining.await())
            val claimed = close.await()
            assertEquals(created.roomId, claimed.fence.roomId)
            assertEquals(created.epoch + 1, claimed.fence.epoch)
            assertEquals(created.revision, claimed.revision)
            assertEquals("Drain fixture", claimed.snapshot.roomName)
            assertTrue(pilot.renderMetrics().contains("fasttowin_room_ownership_routes_total{outcome=\"local\"} 1"))
            assertTrue(pilot.beginDrain())
        }
    }

    @Test
    fun `pilot rejects a mismatched fixture marker and missing ownership schema`() = runBlocking {
        fixture { f ->
            assertFailsWith<IllegalArgumentException> { f.pilot(config = f.config(UUID.randomUUID())) }
            f.source.connection.use { c ->
                c.createStatement().use { it.execute("ALTER TABLE room_ownership_pilot_events RENAME COLUMN events TO broken_events") }
            }
            assertFailsWith<IllegalArgumentException> { f.pilot() }
        }
    }

    @Test
    fun `queued event age measures stored backlog before a delivery attempt`() = runBlocking {
        fixture { f ->
            val pilot = f.pilot()
            val rooms = PostgresRoomOwnershipRepository(f.source)
            val host = rooms.bindSocket(f.players.keys.first())
            pilot.delivery.register(host, null) {}
            pilot.delivery.onCommand(host) { pilot.onMessage(host, CREATE) }
            val roomId = assertNotNull(rooms.lookupRoom(host.playerId))
            val state = assertNotNull(rooms.read(roomId))
            assertIs<FencedResult.Committed>(rooms.withFencedRoom(state.fence, state.revision, host) { _, current ->
                RoomMutation.Change(current.snapshot, listOf(
                    RoomOwnershipEvent(listOf(host.playerId), current.snapshot.publicState())
                ))
            })
            f.source.connection.use { c ->
                c.createStatement().use {
                    it.executeUpdate("UPDATE room_ownership_pilot_events SET created_at = clock_timestamp() - interval '5 seconds'")
                }
            }
            val metrics = pilot.renderMetrics()
            val age = metrics.lineSequence().first { it.startsWith("fasttowin_room_ownership_oldest_queued_event_seconds ") }
                .substringAfter(' ').toDouble()
            assertTrue(age >= 5.0)
            assertTrue(pilot.beginDrain())
        }
    }

    @Test
    fun `pilot JDBC read is bounded even when the server statement deadline is disabled`() = runBlocking {
        fixture { f ->
            val pilot = f.pilot()
            val rooms = PostgresRoomOwnershipRepository(f.source)
            val host = rooms.bindSocket(f.players.keys.first())
            val created = assertIs<OwnedRoomReply.Applied>(pilot.onMessage(host, CREATE))
            val state = assertNotNull(rooms.read(created.roomId))
            val elapsed = measureTime {
                val result = rooms.withFencedRoom(state.fence, state.revision, host) { connection, _ ->
                    connection.createStatement().use {
                        it.execute("SET LOCAL statement_timeout = 0")
                        it.execute("SELECT pg_sleep(7)")
                    }
                    RoomMutation.Unchanged
                }
                assertIs<FencedResult.TemporaryFailure>(result)
            }
            assertTrue(elapsed.inWholeMilliseconds < 5_500, "JDBC must stop waiting before the 7-second query finishes")
            assertEquals(state.revision, rooms.read(created.roomId)?.revision)
            withTimeout(10_000) {
                while (!pilot.beginDrain()) delay(100)
            }
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        if (System.getenv("FASTTOWIN_TEST_ROOM_OWNERSHIP") == "true") requireNotNull(url)
        assumeTrue(url != null, "Isolated PostgreSQL fixture required")
        require(url!!.matches(Regex("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_]+")))
        val schema = "owned_ready_test_" + UUID.randomUUID().toString().replace("-", "")
        fun pool(selectedSchema: String?) = HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 4
            if (selectedSchema != null) this.schema = selectedSchema
        })
        pool(null).use { control ->
            control.connection.use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
            try {
                Flyway.configure().dataSource(control).schemas(schema).defaultSchema(schema).load().migrate()
                pool(schema).use { source ->
                    val auth = AuthenticationService(PostgresAuthRepository(source), passwordHasher = PasswordHasher(1_000))
                    val players = listOf("First", "Second").associate { name ->
                        val account = assertIs<AuthResult.Success>(
                            auth.register("${name.lowercase()}@example.test", "FixturePassword123!", name, "test")
                        ).session
                        UUID.fromString(account.userId) to StoredActivePlayer(account.userId, name, isAccount = true)
                    }
                    val fixtureId = UUID.randomUUID()
                    source.connection.use { c ->
                        c.createStatement().use { it.executeUpdate("UPDATE users SET email_verified_at = clock_timestamp()") }
                        c.prepareStatement("INSERT INTO room_ownership_pilot_fixture (fixture_id) VALUES (?)").use {
                            it.setObject(1, fixtureId)
                            it.executeUpdate()
                        }
                    }
                    block(Fixture(source, players, fixtureId))
                }
            } finally {
                control.connection.use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }

    private data class Fixture(
        val source: HikariDataSource,
        val players: Map<UUID, StoredActivePlayer>,
        val fixtureId: UUID
    ) {
        fun config(id: UUID = fixtureId) = RoomOwnershipPilotConfig(
            UUID.randomUUID(), UUID.randomUUID(), URI("http://127.0.0.1:65534"), ByteArray(32) { 7 }, id, players.keys
        )

        suspend fun pilot(
            dataSource: DataSource = source,
            config: RoomOwnershipPilotConfig = config(),
            lookup: suspend (UUID) -> StoredActivePlayer? = { players[it] }
        ) = RoomOwnershipPilot.open(config, dataSource, lookup, wakeups = emptyFlow())
    }

    private class SwitchableDataSource(private val source: DataSource) : DataSource by source {
        val unavailable = AtomicBoolean()
        override fun getConnection() = if (unavailable.get()) throw SQLException("Fixture disconnect", "08006") else source.connection
    }

    private companion object {
        val CREATE = ClientMessage.CreateRoom("Drain fixture", "private fixture", ProtocolGameMode.ORDER)
    }
}
