package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.PasswordHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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

class PostgresAccountGameLaneTest {
    @Test fun `lane binding requires matching committed authority but STOP_NEW permits revocation`() = runBlocking {
        withAdmissionFixture { f ->
            val original = f.rooms.bindSocket(ADMISSION_HOST)
            assertFailsWith<OwnedAdmissionRejected> { f.rooms.bindSocketForLane(ADMISSION_HOST) }
            assertTrue(f.rooms.isCurrentSocket(original))
            val active = f.activate()
            val mismatch = PostgresRoomOwnershipRepository(f.source,
                admission = OwnedAdmissionBinding.from(admissionProductionConfig(accounts = listOf(ADMISSION_HOST))))
            assertFailsWith<OwnedAdmissionRejected> { mismatch.bindSocketForLane(ADMISSION_HOST) }
            assertTrue(f.rooms.isCurrentSocket(original))
            f.authority.stopNew(active.generation)
            val bound = f.rooms.bindSocketForLane(ADMISSION_HOST)
            assertEquals(original.generation + 1, bound.generation)
            assertTrue(f.rooms.isCurrentSocket(bound))
            assertFalse(f.rooms.isCurrentSocket(original))
            f.execute("DELETE FROM owned_admission_policy")
            assertFailsWith<OwnedAdmissionRejected> { f.rooms.bindSocketForLane(ADMISSION_HOST) }
            assertTrue(f.rooms.isCurrentSocket(bound))
        }
    }

    @Test fun `non opted in production account still binds and reconciles after restart`() = runBlocking {
        withAdmissionFixture { f ->
            val config = admissionProductionConfig(accounts = listOf(ADMISSION_HOST))
            val binding = OwnedAdmissionBinding.from(config)
            val active = f.authority.bootstrap(assertNotNull(f.authority.observe()).generation, binding)
            f.authority.stopNew(active.generation)
            val first = f.pilot(config).bindGameLane(ADMISSION_GUEST)
            assertEquals(1L, first.socket.generation)
            assertEquals(ADMISSION_GUEST, first.socket.playerId)
            assertNull(first.ownedRoomId)
            val restarted = f.pilot(config).bindGameLane(ADMISSION_GUEST)
            assertEquals(2L, restarted.socket.generation)
            assertNull(restarted.ownedRoomId)
            assertFalse(f.unguardedFixtureRooms.isCurrentSocket(first.socket))
            assertTrue(f.unguardedFixtureRooms.isCurrentSocket(restarted.socket))
        }
    }

    @Test fun `held old create commit or rollback precedes barrier and its fresh membership read`() = runBlocking {
        for (commit in listOf(true, false)) withAdmissionFixture { f ->
            val policy = f.activate()
            val old = f.rooms.bindSocket(ADMISSION_HOST)
            val request = f.request(policy, old)
            f.installHeldWrite("INSERT", commit)
            f.source.connection.use { gate ->
                gate.autoCommit = false
                gate.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(807293)") }
                val create = async(Dispatchers.IO) { runCatching { f.create(request) } }
                var binding: kotlinx.coroutines.Deferred<OwnedLaneBinding>? = null
                try {
                    val creator = f.awaitDirectWaiter(f.pid(gate))
                    binding = async(Dispatchers.IO) { f.pilot().bindGameLane(ADMISSION_HOST) }
                    f.awaitBlockedBy(creator)
                    assertFalse(binding.isCompleted, "The committed barrier must wait for the old create's session lock")
                    gate.rollback()
                    val outcome = withTimeout(2_000) { create.await() }
                    if (commit) assertIs<OwnedCreateResult.Created>(outcome.getOrThrow())
                    else assertIs<OwnedCreateResult.Unresolved>(outcome.getOrThrow())
                    val result = withTimeout(2_000) { binding.await() }
                    assertEquals(old.generation + 1, result.socket.generation)
                    assertEquals(if (commit) request.roomId else null, result.ownedRoomId,
                        "Membership must be queried after the held operation commits or rolls back")
                    assertTrue(f.rooms.isCurrentSocket(result.socket))
                    assertFalse(f.rooms.isCurrentSocket(old))
                } finally { gate.rollback(); create.cancel(); binding?.cancel() }
            }
            assertEquals(if (commit) 1 else 0, f.count("owned_room_create_operations"))
            assertEquals(if (commit) 1 else 0, f.count("room_ownership_pilot_memberships"))
        }
    }

    @Test fun `held old join commit or rollback precedes barrier and membership reconciliation`() = runBlocking {
        for (commit in listOf(true, false)) withAdmissionFixture { f ->
            val policy = f.activate()
            val room = f.create(policy)
            val old = f.rooms.bindSocket(ADMISSION_GUEST)
            f.installHeldWrite("UPDATE OF snapshot", commit)
            f.source.connection.use { gate ->
                gate.autoCommit = false
                gate.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(807293)") }
                val join = async(Dispatchers.IO) { runCatching {
                    f.rooms.withFencedRoom(room.fence, room.revision, old, admissionPolicy = policy) { _, current ->
                        RoomMutation.Change(current.snapshot.copy(guest = f.players.getValue(ADMISSION_GUEST),
                            pilotSocketGenerations = current.snapshot.pilotSocketGenerations +
                                (ADMISSION_GUEST.toString() to old.generation)))
                    }
                } }
                var binding: kotlinx.coroutines.Deferred<OwnedLaneBinding>? = null
                try {
                    val joiner = f.awaitDirectWaiter(f.pid(gate))
                    binding = async(Dispatchers.IO) { f.pilot().bindGameLane(ADMISSION_GUEST) }
                    f.awaitBlockedBy(joiner)
                    assertFalse(binding.isCompleted)
                    gate.rollback()
                    val outcome = withTimeout(2_000) { join.await() }
                    if (commit) assertIs<FencedResult.Committed>(outcome.getOrThrow())
                    else assertEquals(FencedResult.TemporaryFailure, outcome.getOrThrow())
                    val result = withTimeout(2_000) { binding.await() }
                    assertEquals(if (commit) room.fence.roomId else null, result.ownedRoomId)
                    assertEquals(old.generation + 1, result.socket.generation)
                    assertTrue(f.rooms.isCurrentSocket(result.socket))
                } finally { gate.rollback(); join.cancel(); binding?.cancel() }
            }
            assertEquals(if (commit) 2 else 1, f.count("room_ownership_pilot_memberships"))
        }
    }

    @Test fun `delayed old generation create and join after barrier mutate no durable work`() = runBlocking {
        withAdmissionFixture { f ->
            val policy = f.activate()
            val old = f.rooms.bindSocket(ADMISSION_HOST)
            val request = f.request(policy, old)
            val bound = f.pilot().bindGameLane(ADMISSION_HOST)
            assertNull(bound.ownedRoomId)
            assertIs<OwnedCreateResult.AttemptRejected>(f.create(request))
            assertEquals(0, f.count("owned_room_create_operations"))
            assertEquals(0, f.count("room_ownership_pilot_rooms"))
            assertEquals(0, f.count("room_ownership_pilot_events"))
            val room = f.create(policy)
            val guest = f.rooms.bindSocket(ADMISSION_GUEST)
            f.pilot().bindGameLane(ADMISSION_GUEST)
            val before = listOf(f.count("room_ownership_pilot_memberships"), f.count("room_ownership_pilot_events"))
            assertIs<FencedResult.Stale>(f.rooms.withFencedRoom(room.fence, room.revision, guest,
                admissionPolicy = policy) { _, current ->
                RoomMutation.Change(current.snapshot.copy(guest = f.players.getValue(ADMISSION_GUEST)))
            })
            assertEquals(before, listOf(f.count("room_ownership_pilot_memberships"), f.count("room_ownership_pilot_events")))
            assertNull(f.rooms.lookupRoom(ADMISSION_GUEST))
        }
    }

    @Test fun `failed bind or post barrier read never returns an idle binding`() = runBlocking {
        withAdmissionFixture { f ->
            f.activate()
            val pilot = f.pilot()
            val original = f.rooms.bindSocket(ADMISSION_HOST)
            f.execute("""CREATE FUNCTION reject_lane_bind() RETURNS trigger LANGUAGE plpgsql AS
                'BEGIN RAISE EXCEPTION ''injected binding failure''; END;'""")
            f.execute("CREATE TRIGGER reject_lane_bind BEFORE UPDATE ON room_ownership_pilot_sessions FOR EACH ROW EXECUTE FUNCTION reject_lane_bind()")
            assertFailsWith<SQLException> { pilot.bindGameLane(ADMISSION_HOST) }
            assertTrue(f.rooms.isCurrentSocket(original), "Failed SQL must not silently publish a new generation")
            f.execute("DROP TRIGGER reject_lane_bind ON room_ownership_pilot_sessions")
            f.execute("ALTER TABLE room_ownership_pilot_memberships RENAME TO unavailable_memberships")
            assertFailsWith<SQLException> { pilot.bindGameLane(ADMISSION_HOST) }
            assertFalse(f.rooms.isCurrentSocket(original), "The acknowledged bind may commit but a failed fresh read still cannot authorize idle")
        }
    }

    private fun OwnedAdmissionTestFixture.installHeldWrite(event: String, commit: Boolean) {
        execute("""CREATE FUNCTION hold_lane_write() RETURNS trigger LANGUAGE plpgsql AS
            'BEGIN PERFORM pg_advisory_xact_lock(807293); ${if (commit) "RETURN NEW;" else "RAISE EXCEPTION ''injected old operation rollback'';"} END;'""")
        execute("CREATE TRIGGER hold_lane_write BEFORE $event ON room_ownership_pilot_rooms FOR EACH ROW EXECUTE FUNCTION hold_lane_write()")
    }

    private suspend fun OwnedAdmissionTestFixture.awaitDirectWaiter(blocker: Int): Int = withTimeout(1_000) {
        var waiter: Int? = null
        while (waiter == null) {
            waiter = source.connection.use { c ->
                c.prepareStatement("SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))").use { s ->
                    s.setInt(1, blocker); s.executeQuery().use { r -> if (r.next()) r.getInt(1) else null }
                }
            }
            if (waiter == null) delay(5)
        }
        checkNotNull(waiter)
    }

    private fun OwnedAdmissionTestFixture.request(policy: OwnedAdmissionPolicy, socket: SocketFence) = OwnedCreateRequest(
        UUID.randomUUID(), UUID.randomUUID(), socket, socket.playerId.toString(),
        RoomOwnershipRouting(config).createNode(socket.playerId.toString()), policy,
        ClientMessage.CreateRoom("Lane barrier", "private-password", ProtocolGameMode.ORDER))

    private suspend fun OwnedAdmissionTestFixture.create(request: OwnedCreateRequest): OwnedCreateResult {
        val verifier = PasswordHash.create(request.command.password)
        val initial = snapshot().copy(roomId = request.roomId.toString(), host = players.getValue(request.socket.playerId),
            roomName = request.command.roomName, passwordSalt = verifier.salt, passwordHash = verifier.value,
            pilotConnectionEpoch = 1, pilotSocketGenerations = mapOf(request.socket.playerId.toString() to request.socket.generation))
        return rooms.createOwned(instance, request, initial,
            listOf(RoomOwnershipEvent(setOf(request.socket.playerId), ServerMessage.RoomCreated(initial.classicSnapshot()))))
    }
}
