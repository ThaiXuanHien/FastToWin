package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PeerRefreshDeadlineTest {
    @Test fun `a stalled DNS refresh preserves the command deadline`() {
        val address = InetAddress.getByName("127.0.0.1")
        // The socket accepts TCP handshakes but never replies to HTTP. Only the peer deadline ends the call.
        ServerSocket(0, 1, address).use { endpoint ->
            val lookup = CountDownLatch(1)
            val release = CountDownLatch(1)
            val lookups = AtomicInteger()
            transport(endpoint.localPort) {
                if (lookups.incrementAndGet() > 1) {
                    lookup.countDown()
                    release.awaitBarrier("release slow DNS lookup")
                }
                listOf(address)
            }.use { peer ->
                val refresh = background { peer.refreshPrivatePeer() }
                try {
                    lookup.awaitBarrier("start DNS refresh")
                    val sender = background { runBlocking { peer.forward(command()) } }
                    assertNull(sender.whileHeld("command deadline must expire while DNS is held"))
                } finally {
                    release.countDown()
                    refresh.get(5, TimeUnit.SECONDS)
                }
            }
        }
    }

    @Test fun `close wins while a fresh private answer is pending`() {
        val lookup = CountDownLatch(1)
        val release = CountDownLatch(1)
        val lookups = AtomicInteger()
        transport(65534) {
            if (lookups.incrementAndGet() == 1) listOf(InetAddress.getByName("127.0.0.1"))
            else {
                lookup.countDown()
                release.awaitBarrier("release valid DNS answer")
                listOf(InetAddress.getByName("127.0.0.2"))
            }
        }.use { peer ->
            val refresh = background { peer.refreshPrivatePeer() }
            try {
                lookup.awaitBarrier("start DNS refresh")
                background { peer.close() }.whileHeld("close must finish while DNS is held")
                assertFalse(peer.hasValidatedPeer())
            } finally {
                release.countDown()
            }
            assertFalse(refresh.get(5, TimeUnit.SECONDS))
            assertFalse(peer.hasValidatedPeer())
            assertFalse(peer.refreshPrivatePeer())
        }
    }

    @Test fun `detached executor shutdown does not block fresh admission`() {
        val old = InetAddress.getByName("127.0.0.1")
        val invalid = InetAddress.getByName("192.0.2.3")
        val lookups = AtomicInteger()
        transport(65534) { if (lookups.incrementAndGet() == 1) listOf(old) else listOf(old, invalid) }.use { peer ->
            val barrier = PeerRetirementBarrier()
            barrier.install(peer)
            val refresh = background { peer.refreshPrivatePeer() }
            try {
                barrier.shutdownStarted.awaitBarrier("enter detached executor shutdown")
                assertFalse(peer.hasValidatedPeer())
                val sender = background { runBlocking { peer.forward(command()) } }
                assertNull(sender.whileHeld("admission must fail closed while detached executor shutdown is held"))
            } finally {
                barrier.submitted.countDown()
                barrier.retired.countDown()
                barrier.releaseAdmission.countDown()
                barrier.shutdownNow()
            }
            assertFalse(refresh.get(5, TimeUnit.SECONDS))
        }
    }

    private fun command() = OwnerCommandRequest(UUID.randomUUID().toString(), 1,
        UUID.randomUUID().toString(), 1, OwnerOperation.DISCONNECT, ownerInstanceId = UUID.randomUUID().toString())

    private fun transport(port: Int, resolve: (String) -> List<InetAddress>) =
        OwnerPeerTransport(UUID.randomUUID(), UUID.randomUUID(), URI("http://worker.railway.internal:$port"),
            ByteArray(32) { 7 }, resolvePeer = resolve).also { peer ->
            assertTrue(peer.refreshPrivatePeer())
        }

    private fun <T> background(work: () -> T) = FutureTask(work).also { Thread(it).start() }

    private fun <T> FutureTask<T>.whileHeld(expectation: String): T = try {
        get(3_500, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
        fail(expectation)
    }
}
