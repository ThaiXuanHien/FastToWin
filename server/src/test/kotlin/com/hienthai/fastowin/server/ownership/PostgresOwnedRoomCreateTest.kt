package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.PasswordHash
import com.hienthai.fastowin.server.StoredActiveRoom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import java.sql.SQLException
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

class PostgresOwnedRoomCreateTest {
    @Test fun `pre V74 fixture room still closes without any create journal`() = runBlocking {
        withAdmissionFixture(migrationTarget = "73") { f ->
            val socket = f.unguardedFixtureRooms.bindSocket(ADMISSION_HOST)
            val initial = f.snapshot()
            val room = assertIs<FencedResult.Committed>(f.unguardedFixtureRooms.createRoom(f.instance, socket, initial)).state
            assertTrue(f.unguardedFixtureRooms.schemaReady())
            assertIs<FencedResult.Committed>(f.unguardedFixtureRooms.withFencedRoom(room.fence, room.revision, socket) { _, state ->
                RoomMutation.Change(state.snapshot, close = true)
            })
            assertNull(f.unguardedFixtureRooms.lookupRoom(ADMISSION_HOST))
        }
    }

    @Test fun `production readiness requires complete create journal columns and uniqueness`() = runBlocking {
        withAdmissionFixture { f ->
            f.activate()
            val pilot = f.pilot()
            val worker = launch(Dispatchers.Default) { pilot.runWorker() }
            try {
                withTimeout(8_000) { while (!pilot.isReady()) kotlinx.coroutines.delay(10) }
                val degradations = listOf(
                    "ALTER TABLE owned_room_create_operations RENAME COLUMN command_sha256 TO missing_command_sha256" to
                        "ALTER TABLE owned_room_create_operations RENAME COLUMN missing_command_sha256 TO command_sha256",
                    "ALTER TABLE owned_room_create_operations DROP CONSTRAINT owned_room_create_operations_pkey" to
                        "ALTER TABLE owned_room_create_operations ADD PRIMARY KEY(operation_id)",
                    "ALTER TABLE owned_room_create_operations DROP CONSTRAINT owned_room_create_operations_room_id_key" to
                        "ALTER TABLE owned_room_create_operations ADD UNIQUE(room_id)"
                )
                for ((degrade, restore) in degradations) {
                    f.execute(degrade)
                    assertFalse(f.rooms.schemaReady(), degrade)
                    assertFalse(pilot.isReady(), degrade)
                    assertTrue(f.unguardedFixtureRooms.schemaReady(), "Fixture schema checks must keep their existing contract")
                    f.execute(restore)
                    assertTrue(f.rooms.schemaReady())
                    assertTrue(pilot.isReady())
                }
            } finally { worker.cancelAndJoin(); pilot.beginDrain() }
        }
    }

    @Test fun `V74 preserves populated V73 rooms events memberships fixture and policy`() = runBlocking {
        withAdmissionFixture(migrationTarget = "73") { f ->
            val policy = f.activate()
            val room = f.create(policy)
            val before = f.roomRow(room.fence.roomId)
            val fixture = UUID.randomUUID()
            f.execute("INSERT INTO room_ownership_pilot_fixture(singleton, fixture_id) VALUES(true, '$fixture')")
            f.migrateLatest()
            assertEquals(before, f.roomRow(room.fence.roomId))
            assertEquals(policy, f.authority.observe())
            assertTrue(f.unguardedFixtureRooms.fixtureReady(fixture))
            assertCounts(f, 0, 1, 1, 1)
        }
    }

    @Test fun `journal constraints reject every partial terminal tuple`() = runBlocking {
        withAdmissionFixture { f ->
            val request = f.request(f.activate())
            assertIs<OwnedCreateResult.Created>(f.create(request))
            for (change in listOf("closed_at=clock_timestamp()", "closed_epoch=1", "closed_revision=2",
                "closed_at=clock_timestamp(),closed_epoch=1", "closed_at=clock_timestamp(),closed_revision=2",
                "closed_epoch=1,closed_revision=2")) {
                val failure = assertFailsWith<SQLException> { f.execute("UPDATE owned_room_create_operations SET $change") }
                assertEquals("23514", failure.sqlState)
            }
            assertIs<OwnedCreateResult.Created>(f.create(request))
        }
    }

    @Test fun `concurrent identical descriptors commit one room membership event and journal`() = runBlocking {
        withAdmissionFixture { f ->
            val policy = f.activate()
            val request = f.request(policy)
            val start = CompletableDeferred<Unit>()
            val results = coroutineScope {
                val calls = (1..4).map { async(Dispatchers.IO) { start.await(); f.create(request) } }
                start.complete(Unit)
                calls.awaitAll()
            }.map { assertIs<OwnedCreateResult.Created>(it) }
            assertEquals(1, results.count { !it.replayed })
            assertEquals(setOf(request.roomId), results.map { it.proof.roomId }.toSet())
            assertCounts(f, 1, 1, 1, 1)
            val state = assertNotNull(f.rooms.read(request.roomId))
            assertEquals(1L, state.revision)
            val before = f.roomRow(request.roomId)
            val replay = assertIs<OwnedCreateResult.Created>(f.create(request))
            assertEquals(results.first().proof, replay.proof)
            assertEquals(before, f.roomRow(request.roomId))
        }
    }

    @Test fun `committed replay precedes STOP_NEW and never reconnects current membership`() = runBlocking {
        withAdmissionFixture { f ->
            val policy = f.activate()
            val request = f.request(policy)
            val original = assertIs<OwnedCreateResult.Created>(f.create(request))
            f.authority.stopNew(policy.generation)
            val before = f.roomRow(request.roomId)
            assertEquals(original.copy(replayed = true), f.create(request))
            assertEquals(before, f.roomRow(request.roomId))
            assertIs<OwnedCreateResult.AttemptRejected>(f.create(request.copy(operationId = UUID.randomUUID(), roomId = UUID.randomUUID())))
            assertCounts(f, 1, 1, 1, 1)
        }
    }

    @Test fun `terminal proof survives close purge restart and membership in another room`() = runBlocking {
        withAdmissionFixture { f ->
            val policy = f.activate()
            val request = f.request(policy)
            val original = assertIs<OwnedCreateResult.Created>(f.create(request))
            val room = assertNotNull(f.rooms.read(request.roomId))
            assertIs<FencedResult.Committed>(f.rooms.withFencedRoom(room.fence, room.revision, request.socket) { _, current ->
                RoomMutation.Change(current.snapshot, close = true)
            })
            val closed = assertIs<OwnedCreateResult.Closed>(f.create(request))
            assertEquals(original.proof, closed.proof)
            f.execute("UPDATE room_ownership_pilot_rooms SET closed_at = clock_timestamp() - interval '3 minutes'")
            assertEquals(1, f.rooms.purgeExpiredClosedRooms(10))
            assertNull(f.rooms.read(request.roomId))
            val next = request.copy(operationId = UUID.randomUUID(), roomId = UUID.randomUUID())
            assertIs<OwnedCreateResult.Created>(f.create(next))
            val restarted = PostgresRoomOwnershipRepository(f.source, admission = f.binding)
            val nextBefore = f.roomRow(next.roomId)
            assertEquals(closed, f.create(request, restarted))
            assertEquals(next.roomId, f.rooms.lookupRoom(request.socket.playerId))
            assertEquals(nextBefore, f.roomRow(next.roomId))
            assertIs<OwnedCreateResult.AttemptRejected>(f.create(request.copy(command = request.command.copy(password = "different")), restarted))
            assertCounts(f, 2, 1, 1, 1)
        }
    }

    @Test fun `collision changed payload and replaced socket disclose no proof or orphan room`() = runBlocking {
        withAdmissionFixture { f ->
            val policy = f.activate()
            val request = f.request(policy)
            assertIs<OwnedCreateResult.Created>(f.create(request))
            val guest = f.rooms.bindSocket(ADMISSION_GUEST)
            val changed = listOf(
                request.copy(command = request.command.copy(password = "wrong")),
                request.copy(command = request.command.copy(roomName = "Changed")),
                request.copy(command = request.command.copy(roomName = "Durable create ")),
                request.copy(command = request.command.copy(gameMode = ProtocolGameMode.RANDOM_TARGET)),
                request.copy(command = request.command.copy(matchType = MatchType.RANKED)),
                request.copy(socket = request.socket.copy(generation = request.socket.generation + 1)),
                request.copy(roomId = UUID.randomUUID()),
                request.copy(socket = guest, routeKey = guest.playerId.toString()),
                request.copy(policy = policy.copy(generation = policy.generation + 1)),
                request.copy(destinationNodeId = UUID.randomUUID())
            )
            changed.forEach { assertIs<OwnedCreateResult.AttemptRejected>(f.create(it)) }
            f.rooms.bindSocket(ADMISSION_HOST)
            assertIs<OwnedCreateResult.AttemptRejected>(f.create(request))
            assertCounts(f, 1, 1, 1, 1)
        }
    }

    @Test fun `SQL failure in journal insert rolls room membership and outbox back`() = runBlocking {
        withAdmissionFixture { f ->
            val policy = f.activate()
            val request = f.request(policy)
            f.execute("""CREATE FUNCTION reject_create_operation() RETURNS trigger LANGUAGE plpgsql AS
                ${'$'}${'$'} BEGIN RAISE EXCEPTION 'injected journal failure'; END ${'$'}${'$'}""")
            f.execute("""CREATE TRIGGER reject_create_operation BEFORE INSERT ON owned_room_create_operations
                FOR EACH ROW EXECUTE FUNCTION reject_create_operation()""")
            assertIs<OwnedCreateResult.Unresolved>(f.create(request))
            assertCounts(f, 0, 0, 0, 0)
            f.execute("DROP TRIGGER reject_create_operation ON owned_room_create_operations")
            assertIs<OwnedCreateResult.Created>(f.create(request))
            assertCounts(f, 1, 1, 1, 1)
        }
    }

    @Test fun `held policy UPDATE commits STOP_NEW before waiting create can mutate`() = runBlocking {
        withAdmissionFixture { f ->
            val policy = f.activate()
            val request = f.request(policy)
            f.source.connection.use { stop ->
                stop.autoCommit = false
                stop.createStatement().use { it.executeUpdate("UPDATE owned_admission_policy SET state='STOP_NEW', generation=nextval('owned_admission_generation_seq') WHERE singleton") }
                val waiting = async(Dispatchers.IO) { f.create(request) }
                try {
                    f.awaitBlockedBy(f.pid(stop))
                    assertFalse(waiting.isCompleted)
                    stop.commit()
                    assertIs<OwnedCreateResult.AttemptRejected>(withTimeout(2_000) { waiting.await() })
                } finally { stop.rollback(); waiting.cancel() }
            }
            assertCounts(f, 0, 0, 0, 0)
        }
    }

    @Test fun `both duplicate transactions hold policy SHARE until one create and one replay commit before STOP_NEW`() = runBlocking {
        withAdmissionFixture { f ->
            val policy = f.activate()
            val request = f.request(policy)
            f.source.connection.use { barrier ->
                barrier.autoCommit = false
                barrier.prepareStatement("SELECT player_id FROM room_ownership_pilot_sessions WHERE player_id=? FOR UPDATE").use {
                    it.setObject(1, request.socket.playerId); it.executeQuery().close()
                }
                val first = async(Dispatchers.IO) { f.create(request) }
                val second = async(Dispatchers.IO) { f.create(request) }
                var stop: kotlinx.coroutines.Deferred<OwnedAdmissionPolicy>? = null
                try {
                    // PostgreSQL may queue the second session waiter behind the first waiter,
                    // so follow the actual blocker graph instead of requiring two direct edges.
                    withTimeout(800) { while (f.sessionWaitersWithPolicyShare(f.pid(barrier)) < 2) kotlinx.coroutines.delay(5) }
                    stop = async(Dispatchers.IO) { f.authority.stopNew(policy.generation) }
                    withTimeout(800) { while (!f.blockedStopExists()) kotlinx.coroutines.delay(5) }
                    assertFalse(stop.isCompleted)
                    barrier.rollback()
                    val results = listOf(first.await(), second.await()).map { assertIs<OwnedCreateResult.Created>(it) }
                    assertEquals(1, results.count { !it.replayed })
                    assertEquals(OwnedAdmissionState.STOP_NEW, stop.await().state)
                } finally { barrier.rollback(); first.cancel(); second.cancel(); stop?.cancel() }
            }
            assertCounts(f, 1, 1, 1, 1)
        }
    }

    @Test fun `historical proof after PLAYING and takeover changes no current state or owner`() = runBlocking {
        withAdmissionFixture { f ->
            val policy = f.activate()
            val request = f.request(policy)
            val created = assertIs<OwnedCreateResult.Created>(f.create(request))
            val initial = assertNotNull(f.rooms.read(request.roomId))
            assertIs<FencedResult.Committed>(f.rooms.withFencedRoom(initial.fence, initial.revision, request.socket,
                admissionPolicy = policy, newIntent = true) { _, state -> RoomMutation.Change(f.playing(state)) })
            val playing = f.roomRow(request.roomId)
            assertEquals(created.copy(replayed = true), f.create(request))
            assertEquals(playing, f.roomRow(request.roomId))
            f.execute("UPDATE room_ownership_pilot_rooms SET lease_until=clock_timestamp() - interval '1 second'")
            val successor = assertNotNull(f.rooms.claim(request.roomId, UUID.randomUUID()))
            val afterTakeover = f.roomRow(request.roomId)
            assertEquals(created.copy(replayed = true), f.create(request))
            assertEquals(afterTakeover, f.roomRow(request.roomId))
            assertTrue(successor.fence.epoch > created.proof.createdEpoch)
            assertIs<FencedResult.Stale>(f.rooms.withFencedRoom(initial.fence, successor.revision, request.socket) { _, state ->
                RoomMutation.Change(state.snapshot)
            })
        }
    }

    @Test fun `concurrent global operation collision across actors never leaks winner or leaves orphan`() = runBlocking {
        withAdmissionFixture { f ->
            val policy = f.activate()
            val host = f.request(policy)
            val guestSocket = f.rooms.bindSocket(ADMISSION_GUEST)
            val guest = host.copy(roomId = UUID.randomUUID(), socket = guestSocket, routeKey = guestSocket.playerId.toString())
            val start = CompletableDeferred<Unit>()
            val results = coroutineScope {
                val calls = listOf(host, guest).map { request -> async(Dispatchers.IO) { start.await(); request to f.create(request) } }
                start.complete(Unit); calls.awaitAll()
            }
            val winners = results.filter { it.second is OwnedCreateResult.Created }
            assertEquals(1, winners.size)
            assertEquals(winners.single().first.roomId, (winners.single().second as OwnedCreateResult.Created).proof.roomId)
            assertTrue(results.filter { it !in winners }.all { it.second is OwnedCreateResult.AttemptRejected || it.second is OwnedCreateResult.Unresolved })
            assertCounts(f, 1, 1, 1, 1)
        }
    }

    @Test fun `different operations for one actor retain one membership and one committed room`() = runBlocking {
        withAdmissionFixture { f ->
            val request = f.request(f.activate())
            val start = CompletableDeferred<Unit>()
            val results = coroutineScope {
                val calls = listOf(request, request.copy(operationId = UUID.randomUUID(), roomId = UUID.randomUUID()))
                    .map { r -> async(Dispatchers.IO) { start.await(); f.create(r) } }
                start.complete(Unit); calls.awaitAll()
            }
            assertEquals(1, results.count { it is OwnedCreateResult.Created })
            assertEquals(1, results.count { it is OwnedCreateResult.AttemptRejected })
            assertCounts(f, 1, 1, 1, 1)
        }
    }

    @Test fun `different actors colliding on assigned room commit only the winning operation`() = runBlocking {
        withAdmissionFixture { f ->
            val host = f.request(f.activate())
            val guestSocket = f.rooms.bindSocket(ADMISSION_GUEST)
            val guest = host.copy(operationId = UUID.randomUUID(), socket = guestSocket, routeKey = guestSocket.playerId.toString())
            val start = CompletableDeferred<Unit>()
            val results = coroutineScope {
                val calls = listOf(host, guest).map { request -> async(Dispatchers.IO) { start.await(); request to f.create(request) } }
                start.complete(Unit); calls.awaitAll()
            }
            val winner = results.filter { it.second is OwnedCreateResult.Created }.single()
            assertEquals(winner.first.operationId, (winner.second as OwnedCreateResult.Created).proof.operationId)
            assertEquals(1, results.count { it.second is OwnedCreateResult.AttemptRejected })
            assertCounts(f, 1, 1, 1, 1)
            assertEquals(winner.first.socket.playerId.toString(), f.rooms.read(host.roomId)?.snapshot?.host?.playerId)
        }
    }

    @Test fun `close and descriptor replay serialize in production order and return terminal proof`() = runBlocking {
        withAdmissionFixture { f ->
            val request = f.request(f.activate())
            assertIs<OwnedCreateResult.Created>(f.create(request))
            val room = assertNotNull(f.rooms.read(request.roomId))
            f.execute("""CREATE FUNCTION hold_create_close() RETURNS trigger LANGUAGE plpgsql AS
                'BEGIN PERFORM pg_advisory_xact_lock(807274); RETURN NEW; END;'""")
            f.execute("CREATE TRIGGER hold_create_close BEFORE UPDATE OF closed_at ON room_ownership_pilot_rooms FOR EACH ROW EXECUTE FUNCTION hold_create_close()")
            f.source.connection.use { barrier ->
                barrier.autoCommit = false
                barrier.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(807274)") }
                val closing = async(Dispatchers.IO) { f.rooms.withFencedRoom(room.fence, room.revision, request.socket) { _, current ->
                    RoomMutation.Change(current.snapshot, close = true)
                } }
                var replay: kotlinx.coroutines.Deferred<OwnedCreateResult>? = null
                try {
                    f.awaitBlockedBy(f.pid(barrier))
                    val closePid = f.directWaiter(f.pid(barrier))
                    replay = async(Dispatchers.IO) { f.create(request) }
                    f.awaitBlockedBy(closePid)
                    assertFalse(replay.isCompleted)
                    barrier.rollback()
                    assertIs<FencedResult.Committed>(closing.await())
                    assertIs<OwnedCreateResult.Closed>(replay.await())
                } finally { barrier.rollback(); closing.cancel(); replay?.cancel() }
            }
            assertNull(f.rooms.lookupRoom(request.socket.playerId))
            assertCounts(f, 1, 1, 0, 2)
        }
    }

    @Test fun `inverse room then policy lock fails boundedly while journal replay recovers`() = runBlocking {
        withAdmissionFixture { f ->
            val request = f.request(f.activate())
            val original = assertIs<OwnedCreateResult.Created>(f.create(request))
            f.source.connection.use { inverse ->
                inverse.autoCommit = false
                inverse.prepareStatement("SELECT room_id FROM room_ownership_pilot_rooms WHERE room_id=? FOR UPDATE").use {
                    it.setObject(1, request.roomId); it.executeQuery().close()
                }
                val replay = async(Dispatchers.IO) { f.create(request) }
                try {
                    f.awaitBlockedBy(f.pid(inverse))
                    inverse.createStatement().use { it.execute("SET LOCAL lock_timeout='250ms'") }
                    val failure = assertFailsWith<SQLException> {
                        inverse.createStatement().use { it.executeQuery("SELECT generation FROM owned_admission_policy WHERE singleton FOR UPDATE").close() }
                    }
                    assertTrue(failure.sqlState in setOf("55P03", "40P01"))
                    inverse.rollback()
                    assertEquals(original.copy(replayed = true), withTimeout(2_000) { replay.await() })
                    assertNotNull(f.authority.observe())
                } finally { inverse.rollback(); replay.cancel() }
            }
        }
    }

    @Test fun `journal stores original slow verifier without plaintext or password bearing fast digest`() = runBlocking {
        withAdmissionFixture { f ->
            val request = f.request(f.activate())
            assertIs<OwnedCreateResult.Created>(f.create(request))
            f.source.connection.use { c -> c.createStatement().use { s ->
                s.executeQuery("SELECT row_to_json(j)::text AS payload, j.password_salt, j.password_hash, r.snapshot FROM owned_room_create_operations j JOIN room_ownership_pilot_rooms r USING(room_id)").use { rows ->
                    check(rows.next())
                    val verifier = PasswordHash(rows.getBytes("password_salt"), rows.getBytes("password_hash"))
                    assertTrue(verifier.matches(request.command.password))
                    assertFalse(verifier.matches("other"))
                    assertEquals(16, verifier.salt.size); assertEquals(32, verifier.value.size)
                    val snapshot = ProtocolJson.decodeFromString<StoredActiveRoom>(rows.getString("snapshot"))
                    assertContentEquals(snapshot.passwordSalt, verifier.salt)
                    assertContentEquals(snapshot.passwordHash, verifier.value)
                    val payload = rows.getString("payload")
                    assertFalse(payload.contains(request.command.password))
                    val fastPassword = MessageDigest.getInstance("SHA-256").digest(request.command.password.toByteArray()).joinToString("") { "%02x".format(it) }
                    assertFalse(payload.contains(fastPassword))
                    assertTrue(payload.contains("pbkdf2-sha256-120000-v1"))
                }
            } }
        }
    }

    private suspend fun OwnedAdmissionTestFixture.request(policy: OwnedAdmissionPolicy): OwnedCreateRequest {
        val socket = rooms.bindSocket(ADMISSION_HOST)
        return OwnedCreateRequest(UUID.randomUUID(), UUID.randomUUID(), socket, socket.playerId.toString(),
            RoomOwnershipRouting(config).createNode(socket.playerId.toString()), policy,
            ClientMessage.CreateRoom("Durable create", "private-test-password", ProtocolGameMode.ORDER))
    }

    private suspend fun OwnedAdmissionTestFixture.create(
        request: OwnedCreateRequest, repository: PostgresRoomOwnershipRepository = rooms
    ): OwnedCreateResult {
        val verifier = PasswordHash.create(request.command.password)
        val room = snapshot().copy(roomId = request.roomId.toString(), host = players.getValue(request.socket.playerId),
            roomName = request.command.roomName, passwordSalt = verifier.salt, passwordHash = verifier.value,
            pilotConnectionEpoch = 1, pilotSocketGenerations = mapOf(request.socket.playerId.toString() to request.socket.generation))
        return repository.createOwned(instance, request, room,
            listOf(RoomOwnershipEvent(setOf(request.socket.playerId), ServerMessage.RoomCreated(room.classicSnapshot()))))
    }

    private fun assertCounts(f: OwnedAdmissionTestFixture, operations: Int, rooms: Int, members: Int, events: Int) {
        assertEquals(operations, f.count("owned_room_create_operations"))
        assertEquals(rooms, f.count("room_ownership_pilot_rooms"))
        assertEquals(members, f.count("room_ownership_pilot_memberships"))
        assertEquals(events, f.count("room_ownership_pilot_events"))
    }

    private fun OwnedAdmissionTestFixture.roomRow(roomId: UUID): String = source.connection.use { c ->
        c.prepareStatement("SELECT row_to_json(r)::text FROM room_ownership_pilot_rooms r WHERE room_id = ?").use { s ->
            s.setObject(1, roomId)
            s.executeQuery().use { rows -> check(rows.next()); rows.getString(1) }
        }
    }

    private fun OwnedAdmissionTestFixture.blockedStopExists(): Boolean = source.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery("""
            SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE cardinality(pg_blocking_pids(pid))>0
                AND query LIKE '%owned_admission_policy%' AND query LIKE '%FOR UPDATE%')
        """).use { rows -> rows.next(); rows.getBoolean(1) } }
    }

    private fun OwnedAdmissionTestFixture.sessionWaitersWithPolicyShare(root: Int): Int = source.connection.use { c ->
        c.prepareStatement("""
            WITH RECURSIVE waiters AS (
                SELECT pid, pg_blocking_pids(pid) AS blockers FROM pg_stat_activity
                WHERE cardinality(pg_blocking_pids(pid)) > 0
            ), rooted(pid) AS (
                SELECT pid FROM waiters WHERE ? = ANY(blockers)
                UNION SELECT w.pid FROM waiters w JOIN rooted r ON r.pid = ANY(w.blockers)
            )
            SELECT count(*) FROM rooted r JOIN pg_stat_activity a ON a.pid=r.pid
            WHERE a.query LIKE '%room_ownership_pilot_sessions%' AND a.query LIKE '%FOR UPDATE%'
                AND EXISTS(SELECT 1 FROM pg_locks l WHERE l.pid=r.pid AND l.granted
                    AND l.relation='owned_admission_policy'::regclass AND l.mode='RowShareLock')
        """).use { s -> s.setInt(1, root); s.executeQuery().use { rows -> rows.next(); rows.getInt(1) } }
    }

    private fun OwnedAdmissionTestFixture.directWaiter(root: Int): Int = source.connection.use { c ->
        c.prepareStatement("SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))").use { s ->
            s.setInt(1, root); s.executeQuery().use { rows -> check(rows.next()); rows.getInt(1) }
        }
    }
}
