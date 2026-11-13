package com.hienthai.fastowin.server.ownership

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnerPeerRefreshTest {
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
