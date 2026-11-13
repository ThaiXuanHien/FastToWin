package com.hienthai.fastowin.server.ownership

import okhttp3.Call
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.OkHttpClient
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/** Holds real OkHttp work until retirement finishes, including the cancelAll-to-shutdown window. */
internal class PeerRetirementBarrier : AbstractExecutorService() {
    val captured = CountDownLatch(1)
    val releaseAdmission = CountDownLatch(1)
    val shutdownStarted = CountDownLatch(1)
    val submitted = CountDownLatch(1)
    val retired = CountDownLatch(1)
    private val delegate = Executors.newSingleThreadExecutor()

    fun install(peer: OwnerPeerTransport) {
        // Instrument the existing pin without adding a production test hook. Derived control clients
        // share this dispatcher and inherit the event listener through newBuilder().
        val pinField = OwnerPeerTransport::class.java.getDeclaredField("peer").apply { isAccessible = true }
        val pin = checkNotNull(pinField.get(peer))
        val clientField = pin.javaClass.getDeclaredField("client").apply { isAccessible = true }
        val client = clientField.get(pin) as OkHttpClient
        clientField.set(pin, client.newBuilder().dispatcher(Dispatcher(this))
            .eventListener(object : EventListener() {
                override fun callStart(call: Call) {
                    captured.countDown()
                    releaseAdmission.awaitBarrier("release captured call")
                }
            }).build())
    }

    override fun execute(command: Runnable) {
        delegate.execute {
            retired.awaitBarrier("retire old peer before network I/O")
            command.run()
        }
        submitted.countDown()
    }

    override fun shutdown() {
        shutdownStarted.countDown()
        submitted.awaitBarrier("register captured call before executor shutdown")
        delegate.shutdown()
    }

    override fun shutdownNow(): MutableList<Runnable> = delegate.shutdownNow()
    override fun isShutdown() = delegate.isShutdown
    override fun isTerminated() = delegate.isTerminated
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = delegate.awaitTermination(timeout, unit)

    fun releaseWhenRetirementContends(retirement: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        // Fixed code blocks retirement on the admission monitor. Broken code reaches shutdown
        // after cancelAll missed the unregistered call. Neither path depends on a timing sleep.
        while (retirement.state != Thread.State.BLOCKED && shutdownStarted.count != 0L &&
            retirement.isAlive && System.nanoTime() < deadline) Thread.yield()
        assertTrue(retirement.state == Thread.State.BLOCKED || shutdownStarted.count == 0L,
            "retirement must contend with the captured sender or reach old-client shutdown")
        releaseAdmission.countDown()
    }
}

internal fun CountDownLatch.awaitBarrier(label: String) =
    assertTrue(await(5, TimeUnit.SECONDS), "Timed out waiting to $label")
