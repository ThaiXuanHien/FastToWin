package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import com.hienthai.fastowin.protocol.ServerMessage

class PostgresOwnedClusterDrainTest {
    @Test
    fun `stopped owner closes waiting room with committed notifications and rejects stale drain`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val (state, _) = f.waitingPair(active)
            val engine = OwnedRoomEngine(f.instance, f.rooms, f.economy, { f.players[it] })
            val stopped = f.authority.stopNew(active.generation)
            engine.closeWaitingForDrain(stopped.generation)
            val closed = assertNotNull(f.rooms.read(state.fence.roomId))
            assertTrue(closed.closed)
            assertEquals(0, f.count("room_ownership_pilot_memberships"))
            val events = assertIs<RoomEventBatch.Contiguous>(f.rooms.readEvents(state.fence.roomId, state.revision, 10))
            val notice = events.revisions.single().events.single()
            assertIs<ServerMessage.RoomClosed>(notice.message)
            assertEquals(setOf(ADMISSION_HOST, ADMISSION_GUEST), notice.recipients)
            val resumed = f.authority.resume(stopped.generation, f.binding)
            val next = f.create(resumed)
            engine.closeWaitingForDrain(stopped.generation)
            assertFalse(assertNotNull(f.rooms.read(next.fence.roomId)).closed)
        }
    }
    @Test
    fun `phase transitions require exact stopped generation binding and a global durable zero`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val socket = f.rooms.bindSocket(ADMISSION_HOST)
            f.rooms.createRoom(f.instance, socket, f.snapshot(), admissionPolicy = active)
            val stopped = f.authority.stopNew(active.generation)
            assertFailsWith<OwnedAdmissionRejected> {
                f.authority.advanceDrain(stopped, f.binding, OwnedAdmissionState.QUIESCING)
            }
            f.execute("UPDATE room_ownership_pilot_rooms SET lease_until=clock_timestamp()-interval '1 second'")
            assertFailsWith<OwnedAdmissionRejected> {
                f.authority.advanceDrain(stopped, f.binding, OwnedAdmissionState.QUIESCING)
            }
            f.execute("UPDATE room_ownership_pilot_rooms SET closed_at=clock_timestamp(), snapshot=jsonb_set(snapshot, '{pendingCompletedMatch}', '{}'::jsonb)")
            assertFailsWith<OwnedAdmissionRejected> {
                f.authority.advanceDrain(stopped, f.binding, OwnedAdmissionState.QUIESCING)
            }
            f.execute("UPDATE room_ownership_pilot_rooms SET snapshot=snapshot-'pendingCompletedMatch'")
            assertFailsWith<OwnedAdmissionRejected> {
                f.authority.advanceDrain(stopped, f.binding.copy(policySha256 = "0".repeat(64)), OwnedAdmissionState.QUIESCING)
            }
            val quiescing = f.authority.advanceDrain(stopped, f.binding, OwnedAdmissionState.QUIESCING)
            assertTrue(quiescing.generation > stopped.generation)
            assertEquals(OwnedAdmissionState.QUIESCING, quiescing.state)
            assertFailsWith<OwnedAdmissionRejected> {
                f.authority.advanceDrain(stopped, f.binding, OwnedAdmissionState.QUIESCING)
            }
            val disabled = f.authority.advanceDrain(quiescing, f.binding, OwnedAdmissionState.DISABLED)
            assertTrue(disabled.generation > quiescing.generation)
            assertEquals(OwnedAdmissionState.DISABLED, disabled.state)
            assertFailsWith<OwnedAdmissionRejected> { f.authority.resume(disabled.generation, f.binding) }
        }
    }
}
