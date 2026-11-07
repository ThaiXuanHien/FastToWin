package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.GAME_NUMBER_COUNT
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.PostgresGuestIdentityRepository
import com.hienthai.fastowin.server.PostgresMatchResultRepository
import com.hienthai.fastowin.server.PostgresPlayQuotaRepository
import com.hienthai.fastowin.server.StoredActivePlayer
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnedRoomEngineTest {
    @Test
    fun `private classic lifecycle checks admission and replays only matching selections`() = runBlocking {
        fixture { f ->
            val created = f.create()
            assertEquals(created.roomId, f.a.handle(f.host, CREATE).applied().roomId)
            val id = created.roomId.toString()
            assertEquals("WRONG_PASSWORD", f.a.handle(f.guest, ClientMessage.JoinRoom(id, "wrong")).rejected())
            f.join(id)
            assertEquals("ROOM_FULL", f.a.handle(f.other, ClientMessage.JoinRoom(id, PASSWORD)).rejected())
            f.start(id)
            val one = ClientMessage.SelectNumber(id, 1, "tap")
            val first = f.a.handle(f.host, one).applied()
            val repeat = f.a.handle(f.host, one).applied()
            assertEquals(first.publicMessage, repeat.publicMessage)
            assertEquals(first.revision, repeat.revision)
            assertEquals("REQUEST_ID_REUSED", f.a.handle(f.host, one.copy(number = 2)).rejected())
            assertEquals("NOT_IN_ROOM", f.a.handle(f.other, one).rejected())
            val wrong = f.a.handle(f.guest, one.copy(number = 3, requestId = "wrong")).applied()
            assertEquals("WRONG_NUMBER", assertIs<ServerMessage.Error>(wrong.publicMessage).code)
            assertEquals(wrong.revision, f.a.handle(f.guest, one.copy(number = 3, requestId = "wrong")).applied().revision)

            val forfeit = f.a.handle(f.guest, ClientMessage.LeaveRoom(id)).applied()
            assertEquals(f.host.playerId.toString(), assertIs<ServerMessage.GameFinished>(forfeit.publicMessage).game.winnerPlayerId)
            f.a.renewalTick()
            assertEquals(1, f.matchCount(created.roomId))
            f.a.handle(f.guest, ClientMessage.LeaveRoom(id)).applied()
            assertNull(f.repo.lookupRoom(f.guest.playerId))
            f.a.handle(f.host, ClientMessage.LeaveRoom(id)).applied()
            assertTrue(f.state(created.roomId).closed)
        }
    }

    @Test
    fun `takeover restores a committed move and discards the replaced socket`() = runBlocking {
        fixture { f ->
            val id = f.createPlaying()
            val tap = ClientMessage.SelectNumber(id.toString(), 1, "lost-reply")
            f.a.handle(f.host, tap).applied() // Simulate a response dropped by the caller.
            val before = f.state(id)
            f.expireLease(id)
            f.b.renewalTick()
            val claimed = f.state(id)
            assertEquals(before.fence.epoch + 1, claimed.fence.epoch)
            assertNotNull(claimed.recoveryDeadline)
            val newHost = f.repoB.bindSocket(f.host.playerId)
            assertIs<OwnedRoomReply.Retryable>(f.b.handle(f.host, tap))
            f.b.disconnect(f.host)
            val recovered = assertIs<ServerMessage.GameStateUpdated>(f.b.reconnect(newHost).applied().publicMessage).game
            assertEquals(before.snapshot.numbers, recovered.numbers)
            assertEquals(listOf(1), recovered.selectedNumbers)
            assertEquals(10, recovered.players.first().score)
            assertEquals(before.snapshot.sequence, recovered.sequence)
            assertIs<OwnedRoomReply.Retryable>(f.b.handle(newHost, tap.copy(number = 2, requestId = "new")))
            f.b.reconnect(f.guest).applied()
            val replay = assertIs<ServerMessage.GameStateUpdated>(f.b.handle(newHost, tap).applied().publicMessage)
            assertEquals(listOf(1), replay.game.selectedNumbers)
            f.b.handle(newHost, tap.copy(number = 2, requestId = "new")).applied()
            assertEquals(listOf(1, 2), f.state(id).snapshot.selectedNumbers)
        }
    }

    @Test
    fun `recovery deadline protects players and new owner retries a pending result`() = runBlocking {
        fixture { f ->
            val id = f.createPlaying()
            f.expireLease(id)
            f.b.renewalTick()
            val recovering = f.state(id)
            f.b.reconnect(f.host).applied()
            f.b.disconnect(f.guest)
            f.b.renewalTick()
            assertEquals(RoomPhase.PLAYING, f.state(id).snapshot.phase)
            assertEquals(recovering.recoveryDeadline, f.state(id).recoveryDeadline)

            f.failSettlements()
            f.expireRecovery(id)
            f.b.renewalTick()
            val pending = f.state(id)
            assertEquals(RoomPhase.FINISHED, pending.snapshot.phase)
            assertEquals(f.host.playerId.toString(), assertNotNull(pending.snapshot.pendingCompletedMatch).winnerPlayerId)
            assertTrue(assertNotNull(pending.snapshot.pilotDisconnectDeadlines[f.guest.playerId.toString()]) >
                assertNotNull(pending.snapshot.finishedAtEpochMillis))
            assertEquals(0, f.matchCount(id))
            f.allowSettlements()
            f.expireLease(id)
            f.a.renewalTick()
            assertEquals(pending.fence.epoch + 1, f.state(id).fence.epoch)
            assertNull(f.state(id).snapshot.pendingCompletedMatch)
            assertEquals(1, f.matchCount(id))
        }
    }

    @Test
    fun `successive owner changes cannot extend an expired recovery window`() = runBlocking {
        fixture { f ->
            val id = f.createPlaying()
            f.expireLease(id)
            f.b.renewalTick()
            f.expireRecovery(id)
            f.expireLease(id)
            f.a.renewalTick()
            assertTrue(f.state(id).closed)
            assertNull(f.repo.lookupRoom(f.host.playerId))
            assertNull(f.repo.lookupRoom(f.guest.playerId))
            assertEquals(0, f.matchCount(id))
        }
    }

    @Test
    fun `replacement socket closing before reattach cannot keep a phantom player online`() = runBlocking {
        fixture { f ->
            val id = f.createPlaying()
            val replacement = f.repoB.bindSocket(f.guest.playerId)
            f.a.disconnect(f.guest)
            assertEquals(f.guest.generation, f.state(id).snapshot.pilotSocketGenerations[f.guest.playerId.toString()])
            f.a.disconnect(replacement)
            assertNull(f.state(id).snapshot.pilotSocketGenerations[f.guest.playerId.toString()])
            f.expireDisconnect(id, f.guest)
            f.a.renewalTick()
            assertEquals(f.host.playerId.toString(), f.state(id).snapshot.forcedWinnerId)
            f.expireDisconnect(id, f.guest)
            val resultsExpired = assertIs<ServerMessage.RoomClosed>(f.a.reconnect(replacement).applied().publicMessage)
            assertEquals("ROOM_DISCONNECTED_TOO_LONG", resultsExpired.code)
            assertNull(f.repo.lookupRoom(f.guest.playerId))
            assertEquals(id, f.repo.lookupRoom(f.host.playerId))
        }
    }

    @Test
    fun `leaving during takeover gives the absent opponent a bounded results grace`() = runBlocking {
        fixture { f ->
            val id = f.createPlaying()
            f.expireLease(id)
            f.b.renewalTick()
            f.b.reconnect(f.host).applied()
            val leave = ClientMessage.LeaveRoom(id.toString())
            val result = assertIs<ServerMessage.GameFinished>(f.b.handle(f.host, leave).applied().publicMessage)
            assertEquals(f.guest.playerId.toString(), result.game.winnerPlayerId)
            assertNotNull(f.state(id).snapshot.pilotDisconnectDeadlines[f.guest.playerId.toString()])
            f.b.handle(f.host, leave).applied()
            f.expireDisconnect(id, f.guest)
            f.b.renewalTick()
            assertTrue(f.state(id).closed)
            assertNull(f.repo.lookupRoom(f.guest.playerId))
            assertEquals(1, f.matchCount(id))
        }
    }

    @Test
    fun `last selector is marked finished but score still decides the match`() = runBlocking {
        fixture { f ->
            val id = f.createPlaying()
            for (number in 1 until GAME_NUMBER_COUNT) {
                f.a.handle(f.host, ClientMessage.SelectNumber(id.toString(), number, "tap-$number")).applied()
            }
            val last = ClientMessage.SelectNumber(id.toString(), GAME_NUMBER_COUNT, "last")
            val game = assertIs<ServerMessage.GameStateUpdated>(f.a.handle(f.guest, last).applied().publicMessage).game
            assertEquals(RoomPhase.FINISHED, game.phase)
            assertEquals(f.host.playerId.toString(), game.winnerPlayerId)
            assertTrue(game.players.single { it.id == f.guest.playerId.toString() }.isFinished)
            assertEquals(f.a.handle(f.guest, last).applied().publicMessage, ServerMessage.GameStateUpdated(
                game, GAME_NUMBER_COUNT, f.guest.playerId.toString()
            ))
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        if (System.getenv("FASTTOWIN_TEST_ROOM_OWNERSHIP") == "true") requireNotNull(url)
        assumeTrue(url != null, "Isolated PostgreSQL fixture required")
        require(url!!.matches(Regex("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_]+")))
        val schema = "owned_engine_test_" + UUID.randomUUID().toString().replace("-", "")
        fun pool(selectedSchema: String?) = HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 3
            if (selectedSchema != null) this.schema = selectedSchema
        })
        pool(null).use { control ->
            control.connection.use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
            try {
                Flyway.configure().dataSource(control).schemas(schema).defaultSchema(schema).load().migrate()
                pool(schema).use { a ->
                    pool(schema).use { b ->
                        val identities = PostgresGuestIdentityRepository(a)
                        val players = listOf("Pilot host", "Pilot guest", "Pilot other").associate { name ->
                            val id = identities.resolveGuest(name, null, System.currentTimeMillis()).playerId
                            UUID.fromString(id) to StoredActivePlayer(id, name, isAccount = true)
                        }
                        a.connection.use { c ->
                            c.createStatement().use {
                                it.executeUpdate("""
                                    UPDATE users SET account_type = 'REGISTERED',
                                        email_normalized = id::text || '@example.test', password_hash = 'fixture-only'
                                """.trimIndent())
                            }
                        }
                        block(Fixture(a, b, players).also { it.bind() })
                    }
                }
            } finally {
                control.connection.use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }

    private class Fixture(
        private val aData: HikariDataSource,
        bData: HikariDataSource,
        private val players: Map<UUID, StoredActivePlayer>
    ) {
        val repo = PostgresRoomOwnershipRepository(aData)
        val repoB = PostgresRoomOwnershipRepository(bData)
        val a = OwnedRoomEngine(
            UUID.randomUUID(), repo, OwnedMatchEconomy(repo, PostgresPlayQuotaRepository(aData), PostgresMatchResultRepository(aData)),
            { players[it] }
        )
        val b = OwnedRoomEngine(
            UUID.randomUUID(), repoB, OwnedMatchEconomy(repoB, PostgresPlayQuotaRepository(bData), PostgresMatchResultRepository(bData)),
            { players[it] }
        )
        lateinit var host: SocketFence
        lateinit var guest: SocketFence
        lateinit var other: SocketFence

        suspend fun bind() {
            val ids = players.keys.toList()
            host = repo.bindSocket(ids[0])
            guest = repoB.bindSocket(ids[1])
            other = repo.bindSocket(ids[2])
        }

        suspend fun create() = a.handle(host, CREATE).applied()
        suspend fun join(id: String) = a.handle(guest, ClientMessage.JoinRoom(id, PASSWORD)).applied()
        suspend fun start(id: String) {
            assertIs<ServerMessage.RoomUpdated>(a.handle(host, ClientMessage.SetReady(id, true)).applied().publicMessage)
            assertIs<ServerMessage.GameStarted>(a.handle(guest, ClientMessage.SetReady(id, true)).applied().publicMessage)
        }

        suspend fun createPlaying(): UUID = create().roomId.also { join(it.toString()); start(it.toString()) }
        suspend fun state(id: UUID) = assertNotNull(repo.read(id))
        fun expireLease(id: UUID) = execute("UPDATE room_ownership_pilot_rooms SET lease_until = clock_timestamp() WHERE room_id = ?", id)
        fun expireRecovery(id: UUID) = execute("UPDATE room_ownership_pilot_rooms SET recovery_deadline = clock_timestamp() WHERE room_id = ?", id)
        fun expireDisconnect(id: UUID, socket: SocketFence) {
            aData.connection.use { c ->
                c.prepareStatement("""
                    UPDATE room_ownership_pilot_rooms
                    SET snapshot = jsonb_set(snapshot, ARRAY['pilotDisconnectDeadlines', ?], '0', false)
                    WHERE room_id = ?
                """.trimIndent()).use {
                    it.setString(1, socket.playerId.toString())
                    it.setObject(2, id)
                    assertEquals(1, it.executeUpdate())
                }
            }
        }
        fun matchCount(id: UUID) = aData.connection.use { c ->
            c.prepareStatement("SELECT count(*) FROM matches WHERE id = ?").use {
                it.setObject(1, id)
                it.executeQuery().use { r -> assertTrue(r.next()); r.getInt(1) }
            }
        }

        fun failSettlements() {
            execute("""
                CREATE FUNCTION reject_pilot_result() RETURNS trigger LANGUAGE plpgsql AS
                'BEGIN RAISE EXCEPTION ''simulated settlement failure''; END;'
            """.trimIndent())
            execute("CREATE TRIGGER reject_pilot_result BEFORE INSERT ON matches FOR EACH ROW EXECUTE FUNCTION reject_pilot_result()")
        }

        fun allowSettlements() = execute("DROP TRIGGER reject_pilot_result ON matches")
        private fun execute(sql: String, id: UUID? = null) {
            aData.connection.use { c ->
                c.prepareStatement(sql).use { statement ->
                    id?.let { statement.setObject(1, it) }
                    statement.execute()
                }
            }
        }
    }

    private companion object {
        const val PASSWORD = "pilot fixture"
        val CREATE = ClientMessage.CreateRoom("Private pilot", PASSWORD, ProtocolGameMode.ORDER)
        fun OwnedRoomReply.applied() = assertIs<OwnedRoomReply.Applied>(this)
        fun OwnedRoomReply.rejected() = assertIs<OwnedRoomReply.Rejected>(this).publicMessage.code
    }
}
