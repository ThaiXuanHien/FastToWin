package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OwnedAdmissionObservationTest {
    @Test
    fun `validated ingress and worker agree on literal canonical fingerprint vectors`() {
        val ingress = OwnedAdmissionBinding.from(admissionProductionConfig())
        val worker = OwnedAdmissionBinding.from(admissionProductionConfig(RoomOwnershipRuntimeRole.OWNED_WORKER,
            accounts = listOf(ADMISSION_GUEST, ADMISSION_HOST)))
        assertEquals("c801dbb250e2253c1e183aba68098d95f7091c927c5b0df9cd34c90ce55e9c0d", ingress.topologySha256)
        assertEquals("9efed50d2d34e5fc92ddd4757bec2c7e6838d0461b2c06013849ad1aa8fa4e9c", ingress.policySha256)
        assertEquals(ingress, worker)
        assertNotEquals(ingress.topologySha256, OwnedAdmissionBinding.from(admissionProductionConfig(
            nodeA = ADMISSION_NODE_B, nodeB = ADMISSION_NODE_A)).topologySha256, "Role assignment is authoritative")
        assertNotEquals(ingress.topologySha256, OwnedAdmissionBinding.from(admissionProductionConfig(
            nodeB = UUID.fromString("00000000-0000-4000-8000-000000000003"))).topologySha256)
        assertNotEquals(ingress.policySha256, OwnedAdmissionBinding.from(admissionProductionConfig(
            accounts = listOf(ADMISSION_HOST))).policySha256)
    }

    @Test
    fun `production startup and restart observe STOP_NEW without activating it`() = runBlocking {
        withAdmissionFixture { f ->
            val initial = assertNotNull(f.authority.observe())
            assertEquals(OwnedAdmissionState.STOP_NEW, initial.state)
            assertFalse(f.pilot().isReady())
            assertFalse(f.pilot().isReady())
            val restarted = assertNotNull(f.authority.observe())
            assertEquals(initial.generation, restarted.generation, "Opening runtimes must not mutate authority")
            assertEquals(initial.state, restarted.state)
            assertEquals(initial.topologySha256, restarted.topologySha256)
            assertEquals(initial.policySha256, restarted.policySha256)
            val pilot = f.pilot()
            val socket = f.rooms.bindSocket(ADMISSION_HOST)
            val reply = pilot.onMessage(socket, ClientMessage.CreateRoom("Blocked", "private", ProtocolGameMode.ORDER))
            assertFalse(reply is OwnedRoomReply.Applied)
            assertEquals(0, f.count("room_ownership_pilot_rooms"))
        }
    }

    @Test
    fun `worker production readiness uses fresh durable policy and STOP_NEW keeps liveness`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val runtime = f.pilot(admissionProductionConfig(RoomOwnershipRuntimeRole.OWNED_WORKER))
            testApplication {
                application { ownedWorkerModule(runtime) }
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
                withTimeout(8_000) {
                    while (client.get("/ready").status != HttpStatusCode.OK) delay(20)
                }
                f.authority.stopNew(active.generation)
                assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ready").status)
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
                assertEquals(HttpStatusCode.NotFound, client.get("/auth/login").status)
            }
        }
    }

    @Test
    fun `a prior healthy ACTIVE observation cannot cover missing authority or configuration mismatch`() = runBlocking {
        withAdmissionFixture { f ->
            f.activate()
            val runtime = f.pilot()
            val worker = launch { runtime.runWorker() }
            try {
                withTimeout(8_000) { while (!runtime.isReady()) delay(20) }
                val mismatch = f.pilot(admissionProductionConfig(accounts = listOf(ADMISSION_HOST)))
                val mismatchWorker = launch { mismatch.runWorker() }
                try {
                    delay(30)
                    assertFalse(mismatch.isReady())
                    val socket = f.rooms.bindSocket(ADMISSION_HOST)
                    assertFalse(mismatch.onMessage(socket,
                        ClientMessage.CreateRoom("Mismatch", "private", ProtocolGameMode.ORDER)) is OwnedRoomReply.Applied)
                } finally { mismatchWorker.cancelAndJoin() }
                f.execute("DELETE FROM owned_admission_policy")
                assertFalse(runtime.isReady())
                assertEquals(0, f.count("room_ownership_pilot_rooms"))
            } finally { worker.cancelAndJoin() }
        }
    }
}
