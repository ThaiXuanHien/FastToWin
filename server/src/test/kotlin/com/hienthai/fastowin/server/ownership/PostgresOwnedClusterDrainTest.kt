package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
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
    fun `renewal cleanup preserves aged closed pending settlement until reconciliation`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            f.create(active)
            val stopped = f.authority.stopNew(active.generation)
            f.execute("UPDATE room_ownership_pilot_rooms SET closed_at=clock_timestamp()-interval '3 minutes', snapshot=jsonb_set(snapshot, '{pendingCompletedMatch}', '{}'::jsonb)")
            val engine = OwnedRoomEngine(f.instance, f.rooms, f.economy, { f.players[it] })
            assertTrue(engine.renewalTick())
            assertEquals(1, f.count("room_ownership_pilot_rooms"))
            assertEquals(1, f.rooms.durableSnapshot().pendingSettlements)
            assertFailsWith<OwnedAdmissionRejected> { f.authority.advanceDrain(stopped, f.binding, OwnedAdmissionState.QUIESCING) }
            f.execute("UPDATE room_ownership_pilot_rooms SET snapshot=snapshot-'pendingCompletedMatch'")
            assertTrue(engine.renewalTick())
            assertEquals(0, f.count("room_ownership_pilot_rooms"))
            assertEquals(OwnedAdmissionState.QUIESCING, f.authority.advanceDrain(stopped, f.binding, OwnedAdmissionState.QUIESCING).state)
        }
    }

    @Test
    fun `resume preparation excludes stale quiesce in both lock orders`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val stopped = f.authority.stopNew(active.generation)
            val quiescing = f.authority.advanceDrain(stopped, f.binding, OwnedAdmissionState.QUIESCING)
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext())
            val entered = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            val closed = java.util.concurrent.atomic.AtomicBoolean()
            val first = scope.async(kotlinx.coroutines.Dispatchers.IO) {
                f.authority.fenceQuiesce(quiescing, f.binding) {
                    entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)); closed.set(true)
                }
            }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val prepare = scope.async(kotlinx.coroutines.Dispatchers.IO) {
                f.authority.prepareResume(quiescing, f.binding)
            }
            kotlinx.coroutines.withTimeout(5_000) {
                while (!f.source.connection.use { c -> c.createStatement().use { q -> q.executeQuery("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%owned_admission_policy%FOR UPDATE%')").use { r -> r.next(); r.getBoolean(1) } } }) kotlinx.coroutines.delay(5)
            }
            assertFalse(prepare.isCompleted)
            release.countDown()
            first.await()
            val prepared = prepare.await()
            assertTrue(closed.get())
            assertEquals(OwnedAdmissionState.STOP_NEW, prepared.state)
            assertFailsWith<OwnedAdmissionRejected> { f.authority.fenceQuiesce(quiescing, f.binding) { error("stale action ran") } }
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
