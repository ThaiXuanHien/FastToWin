package com.hienthai.fastowin.server.ownership

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
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
                    val status = OwnedControlRequest(UUID.randomUUID().toString(), "status", active.generation,
                        candidateSha256 = identity.candidateSha256, topologySha256 = f.binding.topologySha256)
                    val aProcess = assertNotNull(assertNotNull(client.forwardControl(status)).processId)
                    val stopRequest = OwnedControlRequest(operationId, "stop_new", active.generation, expectedProcessId = aProcess,
                        candidateSha256 = identity.candidateSha256, topologySha256 = f.binding.topologySha256)
                    val stop = assertNotNull(client.forwardControl(stopRequest))
                    assertEquals(stop, client.forwardControl(stopRequest), "A lost reply must be safely replayable")
                    assertEquals("mismatch", assertNotNull(client.forwardControl(stopRequest.copy(candidateSha256 = "d".repeat(64)))).outcome)
                    val stopped = assertNotNull(stop.policy)
                    assertEquals(OwnedAdmissionState.STOP_NEW, stopped.state)
                    val stepRequest = OwnedControlRequest(operationId, "step", stopped.generation, expectedProcessId = aProcess,
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
                val statusRequest = OwnedControlRequest(UUID.randomUUID().toString(), "status", quiescing.generation,
                    candidateSha256 = identity.candidateSha256, topologySha256 = f.binding.topologySha256)
                val process = assertNotNull(assertNotNull(client.forwardControl(statusRequest)).processId)
                var current = quiescing
                fun request() = OwnedControlRequest(UUID.randomUUID().toString(), "resume", current.generation,
                    process, identity.candidateSha256, f.binding.topologySha256)
                val pending = assertNotNull(client.forwardControl(request()))
                assertEquals("pending", pending.outcome)
                current = assertNotNull(pending.policy)
                assertEquals(OwnedAdmissionState.STOP_NEW, assertNotNull(f.authority.observe()).state)
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
    fun `mutation requires current coordinator process even after replacement`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val identity = OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64))
            val b = admissionProductionConfig(RoomOwnershipRuntimeRole.OWNED_WORKER)
            suspend fun pilot() = RoomOwnershipPilot.open(f.config, f.source, { f.players[it] }, wakeups = emptyFlow(), candidateIdentity = { identity })
            fun server(pilot: RoomOwnershipPilot) = embeddedServer(Netty, host = "127.0.0.1", port = 0) { routing { pilot.installRoutes(this) } }.start()
            fun client(port: Int) = OwnerPeerTransport(b.nodeId, b.peerId, URI("http://127.0.0.1:$port"), b.peerSecret)
            fun request(op: String, process: String? = null) = OwnedControlRequest(UUID.randomUUID().toString(), op, active.generation,
                process, identity.candidateSha256, f.binding.topologySha256)
            val first = server(pilot())
            try {
                val sender = client(first.engine.resolvedConnectors().single().port)
                val process = assertNotNull(assertNotNull(sender.forwardControl(request("status"))).processId)
                assertEquals("mismatch", assertNotNull(sender.forwardControl(request("stop_new"))).outcome)
                assertEquals("mismatch", assertNotNull(sender.forwardControl(request("stop_new", UUID.randomUUID().toString()))).outcome)
                assertEquals(active, f.authority.observe())
                first.stop(0, 1_000)
                val replacement = server(pilot())
                try {
                    val replacementClient = client(replacement.engine.resolvedConnectors().single().port)
                    assertEquals("mismatch", assertNotNull(replacementClient.forwardControl(request("stop_new", process))).outcome)
                    assertEquals(active, f.authority.observe())
                } finally { replacement.stop(0, 1_000) }
            } finally { first.stop(0, 1_000) }
        }
    }

    @Test
    fun `candidate identity is measured once for each process lifetime`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val dir = java.nio.file.Files.createTempDirectory("owned-candidate")
            val jar = java.nio.file.Files.write(dir.resolve("server.jar"), byteArrayOf(1))
            try {
                fun measure() = OwnedCandidateIdentity.fromPaths(jar, listOf(jar))
                val original = measure()
                val ingress = RoomOwnershipPilot.open(f.config, f.source, { f.players[it] }, wakeups = emptyFlow(), candidateIdentity = ::measure)
                val a = embeddedServer(Netty, host = "127.0.0.1", port = 0) { routing { ingress.installRoutes(this) } }.start()
                try {
                    val b = admissionProductionConfig(RoomOwnershipRuntimeRole.OWNED_WORKER)
                    val client = OwnerPeerTransport(b.nodeId, b.peerId, URI("http://127.0.0.1:${a.engine.resolvedConnectors().single().port}"), b.peerSecret)
                    fun status() = OwnedControlRequest(UUID.randomUUID().toString(), "status", active.generation,
                        candidateSha256 = original.candidateSha256, topologySha256 = f.binding.topologySha256)
                    assertEquals("ok", assertNotNull(client.forwardControl(status())).outcome)
                    java.nio.file.Files.write(jar, byteArrayOf(2))
                    assertEquals("ok", assertNotNull(client.forwardControl(status())).outcome)
                    val replacement = RoomOwnershipPilot.open(f.config, f.source, { f.players[it] }, wakeups = emptyFlow(), candidateIdentity = ::measure)
                    val replaced = embeddedServer(Netty, host = "127.0.0.1", port = 0) { routing { replacement.installRoutes(this) } }.start()
                    try {
                        val other = OwnerPeerTransport(b.nodeId, b.peerId, URI("http://127.0.0.1:${replaced.engine.resolvedConnectors().single().port}"), b.peerSecret)
                        assertEquals("mismatch", assertNotNull(other.forwardControl(status())).outcome)
                    } finally { replaced.stop(0, 1_000) }
                } finally { a.stop(0, 1_000) }
            } finally { java.nio.file.Files.deleteIfExists(jar); java.nio.file.Files.deleteIfExists(dir) }
        }
    }

    @Test
    fun `historical operation fingerprints cannot exhaust current admission control`() = runBlocking {
        withAdmissionFixture { f ->
            var policy = f.activate()
            val identity = OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64))
            val b = admissionProductionConfig(RoomOwnershipRuntimeRole.OWNED_WORKER)
            val worker = RoomOwnershipPilot.open(b, f.source, { f.players[it] }, wakeups = emptyFlow(), candidateIdentity = { identity })
            val bs = embeddedServer(Netty, host = "127.0.0.1", port = 0) { routing { worker.installRoutes(this) } }.start()
            val peerUri = URI("http://127.0.0.1:${bs.engine.resolvedConnectors().single().port}")
            val ca = object : RoomOwnershipRuntimeConfig by f.config { override val peerUri = peerUri }
            val ingress = RoomOwnershipPilot.open(ca, f.source, { f.players[it] }, wakeups = emptyFlow(), candidateIdentity = { identity })
            val aserver = embeddedServer(Netty, host = "127.0.0.1", port = 0) { routing { ingress.installRoutes(this) } }.start()
            try {
                val client = OwnerPeerTransport(b.nodeId, b.peerId, URI("http://127.0.0.1:${aserver.engine.resolvedConnectors().single().port}"), b.peerSecret)
                val status = OwnedControlRequest(UUID.randomUUID().toString(), "status", policy.generation,
                    candidateSha256 = identity.candidateSha256, topologySha256 = f.binding.topologySha256)
                val process = assertNotNull(assertNotNull(client.forwardControl(status)).processId)
                var oldest: OwnedControlRequest? = null
                repeat(130) {
                    val stop = OwnedControlRequest(UUID.randomUUID().toString(), "stop_new", policy.generation, process, identity.candidateSha256, f.binding.topologySha256)
                    if (oldest == null) oldest = stop
                    val stopped = assertNotNull(client.forwardControl(stop))
                    assertEquals("ok", stopped.outcome)
                    policy = assertNotNull(stopped.policy)
                    val resume = OwnedControlRequest(UUID.randomUUID().toString(), "resume", policy.generation, process, identity.candidateSha256, f.binding.topologySha256)
                    val resumed = assertNotNull(client.forwardControl(resume))
                    assertEquals("ok", resumed.outcome)
                    policy = assertNotNull(resumed.policy)
                }
                assertEquals("stale", assertNotNull(client.forwardControl(assertNotNull(oldest))).outcome)
                repeat(130) {
                    val invalid = OwnedControlRequest(UUID.randomUUID().toString(), "step", policy.generation, process, identity.candidateSha256, f.binding.topologySha256)
                    assertEquals("stale", assertNotNull(client.forwardControl(invalid)).outcome)
                }
                val final = OwnedControlRequest(UUID.randomUUID().toString(), "stop_new", policy.generation, process, identity.candidateSha256, f.binding.topologySha256)
                assertEquals("ok", assertNotNull(client.forwardControl(final)).outcome)
            } finally { aserver.stop(0, 1_000); bs.stop(0, 1_000) }
        }
    }

    @Test
    fun `delayed peer quiesce cannot cross prepared resume policy fence`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val stopped = f.authority.stopNew(active.generation)
            val quiescing = f.authority.advanceDrain(stopped, f.binding, OwnedAdmissionState.QUIESCING)
            val identity = OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64))
            val b = admissionProductionConfig(RoomOwnershipRuntimeRole.OWNED_WORKER)
            val worker = RoomOwnershipPilot.open(b, f.source, { f.players[it] }, wakeups = emptyFlow(), candidateIdentity = { identity })
            val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) { routing { worker.installRoutes(this) } }.start()
            try {
                val client = OwnerPeerTransport(f.config.nodeId, f.config.peerId, URI("http://127.0.0.1:${server.engine.resolvedConnectors().single().port}"), f.config.peerSecret)
                val status = OwnedControlRequest(UUID.randomUUID().toString(), "status", quiescing.generation,
                    candidateSha256 = identity.candidateSha256, topologySha256 = f.binding.topologySha256)
                val process = assertNotNull(assertNotNull(client.forwardControl(status)).processId)
                f.source.connection.use { blocker ->
                    blocker.autoCommit = false
                    val pid = blocker.createStatement().use { q ->
                        q.executeQuery("SELECT generation FROM owned_admission_policy WHERE singleton FOR UPDATE").close()
                        q.executeQuery("SELECT pg_backend_pid()").use { rows -> rows.next(); rows.getInt(1) }
                    }
                    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext())
                    val prep = scope.async(Dispatchers.IO) { f.authority.prepareResume(quiescing, f.binding) }
                    try {
                        f.awaitBlockedBy(pid)
                        val request = OwnedControlRequest(UUID.randomUUID().toString(), "quiesce", quiescing.generation,
                            process, identity.candidateSha256, f.binding.topologySha256)
                        val delayed = scope.async(Dispatchers.IO) { client.forwardControl(request) }
                        withTimeout(800) {
                            while (f.source.connection.use { c -> c.prepareStatement("""
                                WITH RECURSIVE waiters AS (SELECT pid, pg_blocking_pids(pid) AS blockers FROM pg_stat_activity
                                    WHERE cardinality(pg_blocking_pids(pid)) > 0), rooted(pid) AS (
                                    SELECT pid FROM waiters WHERE ? = ANY(blockers)
                                    UNION SELECT w.pid FROM waiters w JOIN rooted r ON r.pid = ANY(w.blockers))
                                SELECT count(*) FROM rooted
                            """.trimIndent()).use { q -> q.setInt(1, pid); q.executeQuery().use { r -> r.next(); r.getInt(1) } } } < 2) delay(5)
                        }
                        blocker.rollback()
                        assertEquals(OwnedAdmissionState.STOP_NEW, prep.await().state)
                        assertEquals("stale", assertNotNull(delayed.await()).outcome)
                        assertEquals(true, worker.isAccepting())
                    } finally { blocker.rollback() }
                }
            } finally { server.stop(0, 1_000) }
        }
    }

    @Test
    fun `worker restart remains stopped and rejects an earlier signed create`() = runBlocking {
        withAdmissionFixture { f ->
            val active = f.activate()
            val identity = OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64))
            val ingress = f.pilot()
            val socket = f.rooms.bindSocket(ADMISSION_HOST)
            val descriptor = kotlin.test.assertIs<OwnedCreatePreparation.Ready>(ingress.prepareOwnedCreate(socket,
                com.hienthai.fastowin.protocol.ClientMessage.CreateRoom("Before stop", "private", com.hienthai.fastowin.protocol.ProtocolGameMode.ORDER))).request
            assertEquals(f.config.peerId, descriptor.destinationNodeId)
            val configB = admissionProductionConfig(RoomOwnershipRuntimeRole.OWNED_WORKER)
            suspend fun pilot() = RoomOwnershipPilot.open(configB, f.source, { f.players[it] }, wakeups = emptyFlow(), candidateIdentity = { identity })
            fun server(worker: RoomOwnershipPilot) = embeddedServer(Netty, host = "127.0.0.1", port = 0) { ownedWorkerModule(worker) }.start()
            val old = server(pilot())
            val stopped = f.authority.stopNew(active.generation)
            old.stop(0, 1_000)
            val replacement = pilot()
            val next = server(replacement)
            try {
                val uri = URI("http://127.0.0.1:${next.engine.resolvedConnectors().single().port}")
                val http = HttpClient.newHttpClient()
                assertEquals(200, http.send(HttpRequest.newBuilder(uri.resolve("/health")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode())
                assertEquals(503, http.send(HttpRequest.newBuilder(uri.resolve("/ready")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode())
                val client = OwnerPeerTransport(f.config.nodeId, f.config.peerId, uri, f.config.peerSecret)
                assertFalse(client.forwardCreate(descriptor) is OwnedCreateResult.Created)
                assertEquals(stopped, f.authority.observe())
                assertEquals(0, f.count("room_ownership_pilot_rooms"))
                val status = OwnedControlRequest(UUID.randomUUID().toString(), "status", stopped.generation,
                    candidateSha256 = identity.candidateSha256, topologySha256 = f.binding.topologySha256)
                assertEquals("ok", assertNotNull(client.forwardControl(status)).outcome)
            } finally { next.stop(0, 1_000) }
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
