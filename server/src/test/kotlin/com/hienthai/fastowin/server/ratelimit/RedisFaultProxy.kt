package com.hienthai.fastowin.server.ratelimit

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** A byte transport only: all decisions and writes still execute on the isolated Redis fixture. */
internal class RedisFaultProxy(url: String) : AutoCloseable {
    enum class Mode { PASS, HOLD, DROP, MALFORMED }
    private val target = URI(url).also { require(it.scheme == "redis") { "Only plain Redis transport is supported" } }
    private val server = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
    private val sockets = CopyOnWriteArrayList<Socket>()
    @Volatile var mode = Mode.PASS
    @Volatile var observed = CountDownLatch(1)
    @Volatile private var released = CountDownLatch(1)
    val proxyUrl get() = buildString {
        append("redis://")
        target.rawUserInfo?.let { append(it).append('@') }
        append("127.0.0.1:").append(server.localPort)
        append(target.rawPath.orEmpty())
        target.rawQuery?.let { append('?').append(it) }
    }
    private val acceptor = thread(isDaemon = true) {
        try {
            while (!server.isClosed) {
                val downstream = server.accept()
                val upstream = Socket(target.host, if (target.port == -1) 6379 else target.port)
                sockets.add(downstream); sockets.add(upstream)
                thread(isDaemon = true) { try { downstream.getInputStream().copyTo(upstream.getOutputStream()) } catch (_: Exception) {} }
                thread(isDaemon = true) {
                    try {
                        val input = upstream.getInputStream()
                        val output = downstream.getOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            when (mode) {
                                Mode.PASS -> { output.write(buffer, 0, count); output.flush() }
                                Mode.HOLD -> { observed.countDown(); released.await(5, TimeUnit.SECONDS); if (!downstream.isClosed) { output.write(buffer, 0, count); output.flush() } }
                                Mode.DROP -> { observed.countDown(); downstream.close(); upstream.close(); break }
                                Mode.MALFORMED -> { observed.countDown(); output.write("*2\r\n:99\r\n:0\r\n".toByteArray()); output.flush(); mode = Mode.PASS }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }
    fun release() { mode = Mode.PASS; released.countDown() }
    fun disconnect() { sockets.forEach { try { it.close() } catch (_: Exception) {} }; sockets.clear() }
    override fun close() { release(); server.close(); disconnect(); acceptor.join(1000) }
}
