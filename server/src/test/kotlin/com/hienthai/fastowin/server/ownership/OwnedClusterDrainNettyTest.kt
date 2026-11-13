package com.hienthai.fastowin.server.ownership

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.emptyFlow

class OwnedClusterDrainNettyTest {
    @Test
    fun `two actual pilots execute signed drain and production boolean cannot authorize rollback`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val identity = OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64))
            val configB = admissionProductionConfig(RoomOwnershipRuntimeRole.OWNED_WORKER)
            val worker = RoomOwnershipPilot.open(configB, f.source, { f.players[it] }, wakeups = emptyFlow(),
                candidateIdentity = { identity })
            val bServer = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                routing { worker.installRoutes(this) }
            }.start()
            try {
                val bUri = URI("http://127.0.0.1:${bServer.engine.resolvedConnectors().single().port}")
                val configA = object : RoomOwnershipRuntimeConfig by f.config { override val peerUri = bUri }
                val ingress = RoomOwnershipPilot.open(configA, f.source, { f.players[it] }, wakeups = emptyFlow(),
                    candidateIdentity = { identity })
                val aServer = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                    routing { ingress.installRoutes(this) }
                }.start()
                try {
                    val aUri = URI("http://127.0.0.1:${aServer.engine.resolvedConnectors().single().port}")
                    val client = OwnerPeerTransport(configB.nodeId, configB.peerId, aUri, configB.peerSecret)
                    val operationId = UUID.randomUUID().toString()
                    val stopRequest = OwnedControlRequest(operationId, "stop_new", active.generation,
                        candidateSha256 = identity.candidateSha256, topologySha256 = f.binding.topologySha256)
                    val stop = assertNotNull(client.forwardControl(stopRequest))
                    assertEquals(stop, client.forwardControl(stopRequest), "A lost reply must be safely replayable")
                    assertEquals("mismatch", assertNotNull(client.forwardControl(stopRequest.copy(candidateSha256 = "d".repeat(64)))).outcome)
                    val stopped = assertNotNull(stop.policy)
                    assertEquals(OwnedAdmissionState.STOP_NEW, stopped.state)
                    val stepRequest = OwnedControlRequest(operationId, "step", stopped.generation,
                        candidateSha256 = identity.candidateSha256, topologySha256 = f.binding.topologySha256)
                    val result = assertNotNull(client.forwardControl(stepRequest))
                    assertEquals(result, client.forwardControl(stepRequest), "Proof replay requires fresh process observations")
                    assertEquals("completed", result.outcome)
                    assertNotNull(result.proof)
                    assertEquals(OwnedAdmissionState.DISABLED, assertNotNull(f.authority.observe()).state)
                    assertFalse(ingress.canRollback())
                    assertFalse(worker.canRollback())
                    assertEquals(true, result.proof!!.contains("INITIAL_DRAIN"))
                    // Losing the response and replacing A cannot reuse the old in-memory proof.
                    aServer.stop(0, 1_000)
                    val restarted = RoomOwnershipPilot.open(configA, f.source, { f.players[it] }, wakeups = emptyFlow(),
                        candidateIdentity = { identity })
                    val restartedServer = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                        routing { restarted.installRoutes(this) }
                    }.start()
                    try {
                        val newUri = URI("http://127.0.0.1:${restartedServer.engine.resolvedConnectors().single().port}")
                        val newClient = OwnerPeerTransport(configB.nodeId, configB.peerId, newUri, configB.peerSecret)
                        val disabled = assertNotNull(result.policy)
                        val statusRequest = OwnedControlRequest(UUID.randomUUID().toString(), "status", disabled.generation,
                            candidateSha256 = identity.candidateSha256, topologySha256 = f.binding.topologySha256)
                        val freshStatus = assertNotNull(newClient.forwardControl(statusRequest))
                        val process = assertNotNull(freshStatus.processId)
                        val revalidate = OwnedControlRequest(UUID.randomUUID().toString(), "step", disabled.generation,
                            expectedProcessId = process, candidateSha256 = identity.candidateSha256,
                            topologySha256 = f.binding.topologySha256)
                        val recovered = assertNotNull(newClient.forwardControl(revalidate))
                        assertEquals("completed", recovered.outcome)
                        assertEquals(true, assertNotNull(recovered.proof).contains("REVALIDATION"))
                        assertEquals(disabled, recovered.policy)
                        val awaited = awaitClusterDrain(UUID.randomUUID(), disabled.generation, 5_000,
                            configB.peerId, identity, f.binding, pollMillis = 10, send = newClient::forwardControl)
                        assertEquals("completed", awaited.outcome)
                        assertEquals(disabled, assertNotNull(awaited.lastStatus).policy)
                        assertEquals(true, assertNotNull(awaited.completion?.proof).contains("REVALIDATION"))
                    } finally { restartedServer.stop(0, 1_000) }
                } finally { aServer.stop(0, 1_000) }
            } finally { bServer.stop(0, 1_000) }
        }
    }
    @Test
    fun `explicit resume rejects drained peer and accepts restarted peer after quiescing`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val identity = OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64))
            val configB = admissionProductionConfig(RoomOwnershipRuntimeRole.OWNED_WORKER)
            val firstWorker = RoomOwnershipPilot.open(configB, f.source, { f.players[it] }, wakeups = emptyFlow(), candidateIdentity = { identity })
            val bServer = embeddedServer(Netty, host = "127.0.0.1", port = 0) { routing { firstWorker.installRoutes(this) } }.start()
            val bPort = bServer.engine.resolvedConnectors().single().port
            val configA = object : RoomOwnershipRuntimeConfig by f.config { override val peerUri = URI("http://127.0.0.1:$bPort") }
            val ingress = RoomOwnershipPilot.open(configA, f.source, { f.players[it] }, wakeups = emptyFlow(), candidateIdentity = { identity })
            val aServer = embeddedServer(Netty, host = "127.0.0.1", port = 0) { routing { ingress.installRoutes(this) } }.start()
            try {
                val client = OwnerPeerTransport(configB.nodeId, configB.peerId, URI("http://127.0.0.1:${aServer.engine.resolvedConnectors().single().port}"), configB.peerSecret)
                val stopped = f.authority.stopNew(active.generation)
                val quiescing = f.authority.advanceDrain(stopped, f.binding, OwnedAdmissionState.QUIESCING)
                assertEquals(true, firstWorker.beginDrain())
                fun request() = OwnedControlRequest(UUID.randomUUID().toString(), "resume", quiescing.generation,
                    candidateSha256 = identity.candidateSha256, topologySha256 = f.binding.topologySha256)
                assertEquals("pending", assertNotNull(client.forwardControl(request())).outcome)
                assertEquals(OwnedAdmissionState.QUIESCING, assertNotNull(f.authority.observe()).state)
                bServer.stop(0, 1_000)
                val newWorker = RoomOwnershipPilot.open(configB, f.source, { f.players[it] }, wakeups = emptyFlow(), candidateIdentity = { identity })
                val replacement = embeddedServer(Netty, host = "127.0.0.1", port = bPort) { routing { newWorker.installRoutes(this) } }.start()
                try {
                    var resumed = assertNotNull(client.forwardControl(request()))
                    repeat(5) {
                        if (resumed.outcome != "ok") {
                            kotlinx.coroutines.delay(100)
                            resumed = assertNotNull(client.forwardControl(request()))
                        }
                    }
                    assertEquals("ok", resumed.outcome)
                    assertEquals(OwnedAdmissionState.ACTIVE, assertNotNull(resumed.policy).state)
                    assertEquals(true, assertNotNull(resumed.policy).generation > quiescing.generation)
                } finally { replacement.stop(0, 1_000) }
            } finally { aServer.stop(0, 1_000); bServer.stop(0, 1_000) }
        }
    }

    @Test
    fun `control accepts signed configured peer and rejects unsigned requests`() = runBlocking {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val key = ByteArray(32) { 7 }
        val count = AtomicInteger()
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            val transport = OwnerPeerTransport(a, b, URI("http://127.0.0.1:65534"), key)
            routing { ownerControlRoutes(transport) { request ->
                count.incrementAndGet()
                OwnedControlResponse(request.operationId, request.operation, request.generation, "ok")
            } }
        }.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().single().port
            val uri = URI("http://127.0.0.1:$port")
            val request = OwnedControlRequest(UUID.randomUUID().toString(), "status", 3)
            val raw = HttpRequest.newBuilder(uri.resolve("/internal/room-ownership/control"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")) .build()
            assertEquals(401, HttpClient.newHttpClient().send(raw, HttpResponse.BodyHandlers.discarding()).statusCode())
            val response = OwnerPeerTransport(b, a, uri, key).forwardControl(request)
            assertEquals("ok", assertNotNull(response).outcome)
            assertEquals(1, count.get())
        } finally { server.stop(0, 1000) }
    }
}
