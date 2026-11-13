package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.URI
import java.util.UUID
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnerPeerRefreshTest {
    @Test fun `replacement cancels a captured command before old endpoint admission`() = capturedCall("command", "replace")
    @Test fun `replacement cancels a captured create before old endpoint admission`() = capturedCall("create", "replace")
    @Test fun `replacement cancels a captured control before old endpoint admission`() = capturedCall("control", "replace")
    @Test fun `invalid DNS cancels a captured command and stays unavailable`() = capturedCall("command", "invalid")
    @Test fun `invalid DNS cancels a captured create and stays unavailable`() = capturedCall("create", "invalid")
    @Test fun `invalid DNS cancels a captured control and stays unavailable`() = capturedCall("control", "invalid")
    @Test fun `close cancels a captured command and stays unavailable`() = capturedCall("command", "close")
    @Test fun `close cancels a captured create and stays unavailable`() = capturedCall("create", "close")
    @Test fun `close cancels a captured control and stays unavailable`() = capturedCall("control", "close")

    private fun capturedCall(lane: String, retirement: String) = runBlocking {
        val ingress = UUID.randomUUID()
        val worker = UUID.randomUUID()
        val secret = ByteArray(32) { 7 }
        val old = InetAddress.getByName("127.0.0.2")
        var answers = listOf(old)
        val received = AtomicInteger()
        OwnerPeerTransport(worker, ingress, URI("http://127.0.0.1:65534"), secret).use { receiver ->
            val server = embeddedServer(Netty, host = old.hostAddress, port = 0) {
                routing {
                    ownerCommandRoutes(receiver) { received.incrementAndGet(); OwnedRoomReply.NoRoom }
                    ownerCreateRoutes(receiver) {
                        received.incrementAndGet(); OwnedCreateResult.AttemptRejected("OLD_ENDPOINT")
                    }
                    ownerControlRoutes(receiver) {
                        received.incrementAndGet()
                        OwnedControlResponse(it.operationId, it.operation, it.generation, "old_endpoint")
                    }
                }
            }.start()
            val barrier = PeerRetirementBarrier()
            try {
                val port = server.engine.resolvedConnectors().single().port
                OwnerPeerTransport(ingress, worker, URI("http://worker.railway.internal:$port"), secret,
                    resolvePeer = { answers }).use { peer ->
                    barrier.install(peer)
                    suspend fun send(): Any? = when (lane) {
                        "command" -> peer.forward(OwnerCommandRequest(UUID.randomUUID().toString(), 1,
                            UUID.randomUUID().toString(), 1, OwnerOperation.DISCONNECT,
                            ownerInstanceId = UUID.randomUUID().toString()))
                        "create" -> peer.forwardCreate(OwnedCreateRequest(UUID.randomUUID(), UUID.randomUUID(),
                            SocketFence(ADMISSION_HOST, 1), ADMISSION_HOST.toString(), worker,
                            OwnedAdmissionPolicy(7, OwnedAdmissionState.ACTIVE, "a".repeat(64), "b".repeat(64)),
                            ClientMessage.CreateRoom("Private create", "private-test-password", ProtocolGameMode.ORDER)))
                        else -> peer.forwardControl(OwnedControlRequest(UUID.randomUUID().toString(), "status", 1))
                    }
                    val sender = FutureTask<Any?> { runBlocking { send() } }
                    val senderThread = Thread(sender, "captured-peer-sender").apply { start() }
                    try {
                        barrier.captured.awaitBarrier("capture old client")
                        answers = if (retirement == "replace") listOf(InetAddress.getByName("127.0.0.3"))
                            else listOf(old, InetAddress.getByName("192.0.2.10"))
                        val retire = FutureTask<Boolean> {
                            if (retirement == "close") { peer.close(); false } else peer.refreshPrivatePeer()
                        }
                        val retireThread = Thread(retire, "peer-retirement").apply { start() }
                        try {
                            barrier.releaseWhenRetirementContends(retireThread)
                            assertEquals(retirement == "replace", retire.get(5, TimeUnit.SECONDS))
                        } finally {
                            barrier.releaseAdmission.countDown()
                            barrier.retired.countDown()
                            retireThread.join(5_000)
                        }
                        val result = sender.get(5, TimeUnit.SECONDS)
                        assertEquals(0, received.get(), "retired endpoint received a newly admitted $lane request")
                        if (lane == "create") assertEquals(OwnedCreateResult.Unresolved("CREATE_PEER_UNAVAILABLE"), result)
                        else assertNull(result)
                        assertEquals(retirement == "replace", peer.hasValidatedPeer())
                        if (retirement != "replace") {
                            val unavailable = send()
                            if (lane == "create") assertEquals(OwnedCreateResult.Unresolved("CREATE_PEER_UNAVAILABLE"), unavailable)
                            else assertNull(unavailable)
                        }
                        if (retirement == "close") {
                            answers = listOf(old)
                            assertFalse(peer.refreshPrivatePeer())
                        }
                    } finally {
                        barrier.releaseAdmission.countDown()
                        barrier.retired.countDown()
                        senderThread.join(5_000)
                    }
                }
            } finally {
                barrier.releaseAdmission.countDown()
                barrier.retired.countDown()
                barrier.shutdownNow()
                server.stop(0, 1_000)
            }
        }
    }

    @Test
    fun `replaces the authenticated peer address and fails closed on mixed answers`() = runBlocking {
        val ingress = UUID.randomUUID()
        val worker = UUID.randomUUID()
        val secret = ByteArray(32) { 7 }
        val old = InetAddress.getByName("127.0.0.2")
        val replacement = InetAddress.getByName("127.0.0.3")
        var answers = listOf(old)

        OwnerPeerTransport(worker, ingress, URI("http://127.0.0.1:65534"), secret).use { receiver ->
            fun workerAt(address: InetAddress, port: Int, instance: UUID) =
                embeddedServer(Netty, host = address.hostAddress, port = port) {
                    routing {
                        ownerControlRoutes(receiver) { request ->
                            OwnedControlResponse(request.operationId, request.operation, request.generation,
                                "ok", worker.toString(), instance.toString())
                        }
                    }
                }.start()
            val first = UUID.randomUUID()
            val second = UUID.randomUUID()
            val oldServer = workerAt(old, 0, first)
            try {
                val port = oldServer.engine.resolvedConnectors().single().port
                val newServer = workerAt(replacement, port, second)
                try {
                    OwnerPeerTransport(ingress, worker, URI("http://worker.railway.internal:$port"), secret,
                        resolvePeer = { answers }).use { peer ->
                        suspend fun process(): String? = peer.forwardControl(OwnedControlRequest(
                            UUID.randomUUID().toString(), "status", 1))?.processId

                        assertEquals(first.toString(), process())
                        answers = listOf(replacement)
                        assertTrue(peer.refreshPrivatePeer())
                        assertEquals(second.toString(), process())

                        answers = listOf(replacement, InetAddress.getByName("192.0.2.10"))
                        assertFalse(peer.refreshPrivatePeer())
                        assertFalse(peer.hasValidatedPeer())
                        assertNull(process())

                        answers = emptyList()
                        assertFalse(peer.refreshPrivatePeer())
                        answers = listOf(replacement)
                        assertTrue(peer.refreshPrivatePeer())
                        assertEquals(second.toString(), process())
                    }
                } finally {
                    newServer.stop(0, 1_000)
                }
            } finally {
                oldServer.stop(0, 1_000)
            }
        }
    }
}
