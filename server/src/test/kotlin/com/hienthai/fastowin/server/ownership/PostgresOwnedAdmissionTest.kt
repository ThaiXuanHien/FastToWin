package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.sql.SQLException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresOwnedAdmissionTest {
    @Test
    fun `connection scoped guard refuses autocommit and bootstrap rolls back with the caller`() = runBlocking {
        withAdmissionFixture { f ->
            val initial = assertNotNull(f.authority.observe())
            f.source.connection.use { connection ->
                assertFailsWith<IllegalArgumentException> {
                    f.authority.bootstrapOn(connection, initial.generation, f.binding)
                }
                connection.autoCommit = false
                val uncommitted = f.authority.bootstrapOn(connection, initial.generation, f.binding)
                assertEquals(OwnedAdmissionState.ACTIVE, f.authority.observeOn(connection)?.state)
                assertEquals(OwnedAdmissionState.STOP_NEW, f.authority.observe()?.state,
                    "Other connections must not see activation before the caller commits")
                connection.rollback()
                assertEquals(initial.generation, f.authority.observe()?.generation)
                val activated = f.activate()
                assertTrue(activated.generation > uncommitted.generation, "Rollback cannot reuse an allocated generation")
                connection.autoCommit = true
                assertFailsWith<IllegalArgumentException> {
                    f.authority.requireNewIntent(connection, activated, f.binding)
                }
            }
        }
    }

    @Test
    fun `V73 upgrades populated V72 without changing fixture ownership and starts STOP_NEW`() = runBlocking {
        withAdmissionFixture(migrationTarget = "72") { f ->
            val fixtureId = UUID.randomUUID()
            f.execute("INSERT INTO room_ownership_pilot_fixture(singleton, fixture_id) VALUES(true, '$fixtureId')")
            val initial = f.snapshot()
            val socket = f.unguardedFixtureRooms.bindSocket(ADMISSION_HOST)
            val room = assertIs<FencedResult.Committed>(f.unguardedFixtureRooms.createRoom(f.instance, socket, initial,
                listOf(RoomOwnershipEvent(setOf(ADMISSION_HOST), ServerMessage.RoomCreated(initial.classicSnapshot()))))).state
            fun storedRoom(): String = f.source.connection.use { connection ->
                connection.prepareStatement("SELECT to_jsonb(r)::text FROM room_ownership_pilot_rooms r WHERE room_id=?").use { statement ->
                    statement.setObject(1, room.fence.roomId)
                    statement.executeQuery().use { rows -> check(rows.next()); rows.getString(1) }
                }
            }
            // Compare actual persisted content, including snapshot bytes, lease and metadata; ByteArray data-class equality is referential.
            val before = storedRoom()
            f.migrateLatest()
            assertEquals(before, storedRoom())
            val restored = assertNotNull(f.unguardedFixtureRooms.read(room.fence.roomId))
            assertEquals(room.revision, restored.revision)
            assertEquals(room.fence, restored.fence)
            assertEquals(room.fence.roomId, f.unguardedFixtureRooms.lookupRoom(ADMISSION_HOST))
            assertEquals(1, f.count("room_ownership_pilot_events"))
            assertTrue(f.unguardedFixtureRooms.fixtureReady(fixtureId))
            val policy = assertNotNull(f.authority.observe())
            assertEquals(OwnedAdmissionState.STOP_NEW, policy.state)
            assertTrue(policy.generation > 0)
            assertNull(policy.topologySha256)
            assertNull(policy.policySha256)
            assertEquals(1, f.count("owned_admission_policy"))
        }
    }

    @Test
    fun `bootstrap resume and stop compare and set the persisted generation across restart`() = runBlocking {
        withAdmissionFixture { f ->
            val initial = assertNotNull(f.authority.observe())
            val active = f.activate()
            assertTrue(active.generation > initial.generation)
            assertEquals(OwnedAdmissionState.ACTIVE, active.state)
            assertEquals(f.binding.topologySha256, active.topologySha256)
            assertEquals(f.binding.policySha256, active.policySha256)
            val restarted = PostgresOwnedAdmissionRepository(f.source)
            assertEquals(active.generation, assertNotNull(restarted.observe()).generation)
            val stopped = restarted.stopNew(active.generation)
            assertTrue(stopped.generation > active.generation)
            assertEquals(OwnedAdmissionState.STOP_NEW, stopped.state)
            assertEquals(stopped.generation, f.authority.stopNew(stopped.generation).generation)
            assertFailsWith<OwnedAdmissionRejected> { f.authority.stopNew(active.generation) }
            assertFailsWith<OwnedAdmissionRejected> { f.authority.resume(active.generation, f.binding) }
            val resumed = restarted.resume(stopped.generation, f.binding)
            assertTrue(resumed.generation > stopped.generation)
            assertEquals(OwnedAdmissionState.ACTIVE, resumed.state)
            assertFailsWith<OwnedAdmissionRejected> { f.authority.bootstrap(resumed.generation, f.binding) }
        }
    }

    @Test
    fun `concurrent bootstrap and resume each have exactly one generation CAS winner`() = runBlocking {
        withAdmissionFixture { f ->
            val initial = assertNotNull(f.authority.observe())
            val bootstraps = (1..2).map { async(Dispatchers.IO) {
                runCatching { PostgresOwnedAdmissionRepository(f.source).bootstrap(initial.generation, f.binding) }
            } }.awaitAll()
            assertEquals(1, bootstraps.count { it.isSuccess })
            assertIs<OwnedAdmissionRejected>(bootstraps.single { it.isFailure }.exceptionOrNull())
            val active = bootstraps.single { it.isSuccess }.getOrThrow()
            val stopped = f.authority.stopNew(active.generation)
            val resumes = (1..2).map { async(Dispatchers.IO) {
                runCatching { PostgresOwnedAdmissionRepository(f.source).resume(stopped.generation, f.binding) }
            } }.awaitAll()
            assertEquals(1, resumes.count { it.isSuccess })
            assertIs<OwnedAdmissionRejected>(resumes.single { it.isFailure }.exceptionOrNull())
            assertTrue(resumes.single { it.isSuccess }.getOrThrow().generation > stopped.generation)
        }
    }

    @Test
    fun `missing stopped mismatched and stale policy cannot write a new production room`() = runBlocking {
        withAdmissionFixture { f ->
            val socket = f.rooms.bindSocket(ADMISSION_HOST)
            assertIs<FencedResult.Rejected>(f.rooms.createRoom(f.instance, socket, f.snapshot()))
            val active = f.activate()
            val wrongBinding = OwnedAdmissionBinding.from(admissionProductionConfig(accounts = listOf(ADMISSION_HOST)))
            val mismatchRooms = PostgresRoomOwnershipRepository(f.source, admission = wrongBinding)
            assertIs<FencedResult.Rejected>(mismatchRooms.createRoom(f.instance, socket, f.snapshot(), admissionPolicy = active))
            f.authority.stopNew(active.generation)
            assertIs<FencedResult.Rejected>(f.rooms.createRoom(f.instance, socket, f.snapshot(), admissionPolicy = active))
            f.execute("DELETE FROM owned_admission_policy")
            assertIs<FencedResult.Rejected>(f.rooms.createRoom(f.instance, socket, f.snapshot(), admissionPolicy = active))
            assertEquals(0, f.count("room_ownership_pilot_rooms"))
            assertEquals(0, f.count("room_ownership_pilot_memberships"))
            assertEquals(0, f.count("room_ownership_pilot_events"))
        }
    }

    @Test
    fun `configuration cannot silently rebind stopped or ACTIVE authority`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val mismatch = OwnedAdmissionBinding.from(admissionProductionConfig(nodeB = UUID.randomUUID()))
            assertFailsWith<OwnedAdmissionRejected> { f.authority.resume(active.generation, mismatch) }
            val stopped = f.authority.stopNew(active.generation)
            assertFailsWith<OwnedAdmissionRejected> { f.authority.resume(stopped.generation, mismatch) }
            assertFailsWith<OwnedAdmissionRejected> { f.authority.bootstrap(stopped.generation, mismatch) }
            val observed = assertNotNull(f.authority.observe())
            assertEquals(stopped.generation, observed.generation)
            assertEquals(f.binding.topologySha256, observed.topologySha256)
        }
    }

    @Test
    fun `STOP_NEW rejects direct join and actual economy start without quota or outbox changes`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val (waiting, guest) = f.waitingPair(active)
            val events = f.count("room_ownership_pilot_events")
            f.authority.stopNew(active.generation)
            assertIs<FencedResult.Rejected>(f.economy.start(waiting.fence, waiting.revision, guest,
                f.playing(waiting), admissionPolicy = active))
            assertIs<FencedResult.Rejected>(f.economy.start(waiting.fence, waiting.revision, guest, f.playing(waiting)))
            assertEquals(0, f.count("play_quota_match_consumptions"))
            assertEquals(0, f.count("daily_play_quotas"))
            assertEquals(events, f.count("room_ownership_pilot_events"))
            assertEquals(RoomPhase.WAITING, f.rooms.read(waiting.fence.roomId)?.snapshot?.phase)
            // Even a caller skipping the engine/economy cannot introduce a PLAYING state.
            assertIs<FencedResult.Rejected>(f.rooms.withFencedRoom(waiting.fence, waiting.revision, guest) { _, _ ->
                RoomMutation.Change(f.playing(waiting))
            })
        }
    }

    @Test
    fun `production join mutation cannot bypass authority by omitting the admission expectation`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val waiting = f.create(active)
            val guest = f.rooms.bindSocket(ADMISSION_GUEST)
            f.authority.stopNew(active.generation)
            assertIs<FencedResult.Rejected>(f.rooms.withFencedRoom(waiting.fence, waiting.revision, guest) { _, room ->
                RoomMutation.Change(room.snapshot.copy(guest = f.players.getValue(ADMISSION_GUEST)))
            })
            assertNull(f.rooms.lookupRoom(ADMISSION_GUEST))
            assertEquals(1, f.count("room_ownership_pilot_memberships"))
        }
    }

    @Test
    fun `policy UPDATE held in another transaction fences create join and actual start before commit`() = runBlocking {
        for (kind in AdmissionWrite.entries) withAdmissionFixture { f ->
            val active = f.activate()
            val prepared = prepare(f, active, kind)
            val before = listOf(f.count("room_ownership_pilot_rooms"), f.count("room_ownership_pilot_memberships"),
                f.count("room_ownership_pilot_events"))
            f.source.connection.use { stop ->
                stop.autoCommit = false
                stop.createStatement().use { it.executeUpdate("""
                    UPDATE owned_admission_policy SET state='STOP_NEW',
                        generation=nextval('owned_admission_generation_seq') WHERE singleton
                """.trimIndent()) }
                val blocked = async(Dispatchers.IO) { prepared() }
                try {
                    f.awaitBlockedBy(f.pid(stop))
                    assertFalse(blocked.isCompleted, "$kind must wait on the authority transaction")
                    stop.commit()
                    assertIs<FencedResult.Rejected>(withTimeout(2_000) { blocked.await() })
                } finally { stop.rollback() }
            }
            assertEquals(before, listOf(f.count("room_ownership_pilot_rooms"),
                f.count("room_ownership_pilot_memberships"), f.count("room_ownership_pilot_events")), kind.name)
            assertEquals(0, f.count("play_quota_match_consumptions"))
        }
    }

    @Test
    fun `STOP_NEW waits until create join and actual quota bearing start transactions commit`() = runBlocking {
        for (kind in AdmissionWrite.entries) withAdmissionFixture { f ->
            val active = f.activate()
            val prepared = prepare(f, active, kind)
            val triggerEvent = if (kind == AdmissionWrite.CREATE) "INSERT" else "UPDATE OF snapshot"
            f.execute("""
                CREATE FUNCTION hold_admission_write() RETURNS trigger LANGUAGE plpgsql AS
                'BEGIN PERFORM pg_advisory_xact_lock(807251); RETURN NEW; END;'
            """.trimIndent())
            f.execute("CREATE TRIGGER hold_admission_write BEFORE $triggerEvent ON room_ownership_pilot_rooms " +
                "FOR EACH ROW EXECUTE FUNCTION hold_admission_write()")
            f.source.connection.use { barrier ->
                barrier.autoCommit = false
                barrier.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(807251)") }
                val admitted = async(Dispatchers.IO) { prepared() }
                var stopping: kotlinx.coroutines.Deferred<OwnedAdmissionPolicy>? = null
                try {
                    f.awaitBlockedBy(f.pid(barrier))
                    stopping = async(Dispatchers.IO) { f.authority.stopNew(active.generation) }
                    // The stop must be blocked by the admission transaction, which itself waits on our trigger barrier.
                    withTimeout(800) {
                        while (!blockedStopExists(f)) kotlinx.coroutines.delay(5)
                    }
                    assertFalse(stopping.isCompleted)
                    barrier.rollback()
                    assertIs<FencedResult.Committed>(withTimeout(2_000) { admitted.await() })
                    assertEquals(OwnedAdmissionState.STOP_NEW, withTimeout(2_000) { stopping.await() }.state)
                    if (kind == AdmissionWrite.START) assertEquals(2, f.count("play_quota_match_consumptions"))
                } finally {
                    barrier.rollback()
                    admitted.cancel()
                    stopping?.cancel()
                }
            }
        }
    }

    @Test
    fun `inverse room then policy lock attempt fails boundedly and normal write can finish`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val (waiting, guest) = f.waitingPair(active)
            f.source.connection.use { inverse ->
                inverse.autoCommit = false
                inverse.prepareStatement("SELECT room_id FROM room_ownership_pilot_rooms WHERE room_id=? FOR UPDATE").use {
                    it.setObject(1, waiting.fence.roomId); it.executeQuery().close()
                }
                val normal = async(Dispatchers.IO) {
                    f.rooms.withFencedRoom(waiting.fence, waiting.revision, guest) { _, state ->
                        RoomMutation.Change(state.snapshot.copy(readyPlayerIds = emptySet()))
                    }
                }
                try {
                    f.awaitBlockedBy(f.pid(inverse))
                    inverse.createStatement().use { it.execute("SET LOCAL lock_timeout='250ms'") }
                    val rejected = assertFailsWith<SQLException> {
                        inverse.createStatement().use { it.executeQuery("SELECT generation FROM owned_admission_policy FOR UPDATE").close() }
                    }
                    assertTrue(rejected.sqlState in setOf("55P03", "40P01"), rejected.sqlState)
                    inverse.rollback()
                    assertIs<FencedResult.Committed>(withTimeout(2_000) { normal.await() })
                    assertNotNull(f.authority.observe(), "A failed inverse-order transaction must leave the pool usable")
                } finally { inverse.rollback(); normal.cancel() }
            }
        }
    }

    @Test
    fun `STOP_NEW preserves unready reconnect disconnect renewal and existing selection commands`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val pilot = f.pilot()
            val host = f.rooms.bindSocket(ADMISSION_HOST)
            val guest = f.rooms.bindSocket(ADMISSION_GUEST)
            val created = assertIs<OwnedRoomReply.Applied>(pilot.onMessage(host,
                ClientMessage.CreateRoom("Ongoing", "private", ProtocolGameMode.ORDER)))
            val id = created.roomId.toString()
            assertIs<OwnedRoomReply.Applied>(pilot.onMessage(guest, ClientMessage.JoinRoom(id, "private")))
            assertIs<OwnedRoomReply.Applied>(pilot.onMessage(host, ClientMessage.SetReady(id, true)))
            val stopped = f.authority.stopNew(active.generation)
            assertIs<OwnedRoomReply.Applied>(pilot.onMessage(host, ClientMessage.SetReady(id, false)))
            assertEquals(emptySet(), f.rooms.read(created.roomId)?.snapshot?.readyPlayerIds)
            assertIs<OwnedRoomReply.Applied>(pilot.reconnect(host))
            pilot.onDisconnected(host)
            assertIs<OwnedRoomReply.Applied>(pilot.reconnect(host))
            assertTrue(f.rooms.renew(assertNotNull(f.rooms.findOwner(created.roomId))))
            val resumed = f.authority.resume(stopped.generation, f.binding)
            assertIs<OwnedRoomReply.Applied>(pilot.onMessage(host, ClientMessage.SetReady(id, true)))
            assertIs<OwnedRoomReply.Applied>(pilot.onMessage(guest, ClientMessage.SetReady(id, true)))
            val playing = assertNotNull(f.rooms.read(created.roomId))
            assertEquals(RoomPhase.PLAYING, playing.snapshot.phase)
            f.authority.stopNew(resumed.generation)
            assertIs<OwnedRoomReply.Applied>(pilot.onMessage(host,
                ClientMessage.SelectNumber(id, playing.snapshot.currentTarget, "still-playing")))
            assertEquals(2, f.count("play_quota_match_consumptions"))
        }
    }

    private enum class AdmissionWrite { CREATE, JOIN, START }

    private suspend fun prepare(f: OwnedAdmissionTestFixture, policy: OwnedAdmissionPolicy,
        kind: AdmissionWrite): suspend () -> FencedResult = when (kind) {
        AdmissionWrite.CREATE -> {
            val socket = f.rooms.bindSocket(ADMISSION_HOST)
            val snapshot = f.snapshot()
            suspend { f.rooms.createRoom(f.instance, socket, snapshot, admissionPolicy = policy) }
        }
        AdmissionWrite.JOIN -> {
            val waiting = f.create(policy)
            val guest = f.rooms.bindSocket(ADMISSION_GUEST)
            suspend { f.rooms.withFencedRoom(waiting.fence, waiting.revision, guest, admissionPolicy = policy) { _, room ->
                RoomMutation.Change(room.snapshot.copy(guest = f.players.getValue(ADMISSION_GUEST)))
            } }
        }
        AdmissionWrite.START -> {
            val (waiting, guest) = f.waitingPair(policy)
            suspend { f.economy.start(waiting.fence, waiting.revision, guest, f.playing(waiting), admissionPolicy = policy) }
        }
    }

    private fun blockedStopExists(f: OwnedAdmissionTestFixture): Boolean = f.source.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery("""
            SELECT EXISTS (SELECT FROM pg_stat_activity a WHERE cardinality(pg_blocking_pids(a.pid)) > 0
                AND a.query LIKE '%owned_admission_policy%' AND a.pid <> pg_backend_pid())
        """.trimIndent()).use { r -> r.next(); r.getBoolean(1) } }
    }
}
