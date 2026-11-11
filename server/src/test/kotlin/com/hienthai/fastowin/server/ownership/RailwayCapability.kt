package com.hienthai.fastowin.server.ownership

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.postgresql.ds.PGSimpleDataSource

internal fun requirePending(result: CompletableFuture<*>, millis: Long) {
    try {
        result.get(millis, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
        return
    }
    error("The paused connection returned a response")
}

internal enum class PrivateCapabilityStage {
    READY, BACKEND_PAUSE, BACKEND_RESTART, POSTGRES_PAUSE, REDIS_PAUSE, PROXY,
    PROXY_EXISTING, PROXY_FRESH, PROXY_SURVIVOR_SQL, PROXY_SURVIVOR_BACKEND, PROXY_RESTORE, RESTORED
}
internal class PrivateCapabilityFailure(val stage: PrivateCapabilityStage) : IllegalStateException()

/** Run only against the verified disposable fixture, over the runner's private network. */
internal suspend fun RoomAcceptanceFixture.verifyPrivateCapabilities(
    addresses: Map<String, String>, redisPassword: String, onStage: (PrivateCapabilityStage) -> Unit
) {
    onStage(PrivateCapabilityStage.READY)
    check(redisPassword.matches(Regex("[a-f0-9]{64}")))
    verifyFixture()
    for (node in 0..1) awaitReady(node)
    observedRehearsalPools(metrics())
    faults.verifyHealthy()

    onStage(PrivateCapabilityStage.BACKEND_PAUSE)
    for (node in 0..1) {
        withFault({ faults.backend(node, BackendAction.PAUSE) }, { faults.backend(node, BackendAction.RESUME) }) {
            check(!readiness(node) && readiness(1 - node)) { "Backend pause was not isolated" }
        }
        awaitReady(node)
    }
    onStage(PrivateCapabilityStage.BACKEND_RESTART)
    withFault({ faults.backend(0, BackendAction.KILL) }, { faults.backend(0, BackendAction.START) }) {
        check(!readiness(0) && readiness(1)) { "Backend stop was not isolated" }
    }
    awaitReady(0)

    withContext(Dispatchers.IO) {
        onStage(PrivateCapabilityStage.POSTGRES_PAUSE)
        val pg = postgres(addresses.getValue("pg"), 5432)
        pg.connection.use { connection ->
            val pid = connection.backendPid()
            verifyPending({ faults.store(StoreRole.PG, true) }, { faults.store(StoreRole.PG, false) }, 42) {
                connection.number("SELECT 42")
            }
            check(connection.backendPid() == pid)
        }
        onStage(PrivateCapabilityStage.REDIS_PAUSE)
        RedisCapabilitySocket(addresses.getValue("redis")).use { redis ->
            check(redis.command("AUTH $redisPassword") == "+OK")
            val id = redis.command("CLIENT ID")
            check(id.matches(Regex(":[1-9][0-9]*")))
            verifyPending({ faults.store(StoreRole.REDIS, true) }, { faults.store(StoreRole.REDIS, false) }, "+PONG") {
                redis.command("PING")
            }
            check(redis.command("CLIENT ID") == id)
        }
        onStage(PrivateCapabilityStage.PROXY)
        val paths = listOf(15432, 25432).map { postgres(addresses.getValue("proxy"), it) }
        for (node in 0..1) {
            onStage(PrivateCapabilityStage.PROXY)
            // A reopened listener does not immediately make its backend ready.
            // Let the worker recover before requiring it to survive the next partition.
            for (backend in 0..1) awaitReady(backend)
            val selectedPath = paths[node]
            val otherPath = paths[1 - node]
            selectedPath.connection.use { selected ->
                otherPath.connection.use { other ->
                    check(selected.number("SELECT 42") == 42)
                    val pid = other.backendPid()
                    withFault({ faults.partition(node, true) }, { faults.partition(node, false) }) {
                        onStage(PrivateCapabilityStage.PROXY_EXISTING)
                        requireDisconnected { selected.number("SELECT 42") }
                        onStage(PrivateCapabilityStage.PROXY_FRESH)
                        requireDisconnected { selectedPath.connection.use { it.number("SELECT 42") } }
                        onStage(PrivateCapabilityStage.PROXY_SURVIVOR_SQL)
                        check(other.backendPid() == pid)
                        onStage(PrivateCapabilityStage.PROXY_SURVIVOR_BACKEND)
                        check(readiness(1 - node)) { "Surviving backend is unavailable during partition" }
                        onStage(PrivateCapabilityStage.PROXY_RESTORE)
                    }
                }
            }
            selectedPath.connection.use { check(it.number("SELECT 42") == 42) }
        }
    }
    onStage(PrivateCapabilityStage.RESTORED)
    faults.verifyHealthy()
    for (node in 0..1) awaitReady(node)
    observedRehearsalPools(metrics())
}

private fun RoomAcceptanceFixture.postgres(host: String, port: Int) = PGSimpleDataSource().apply {
    setUrl("jdbc:postgresql://$host:$port/fasttowin_room_pilot")
    user = "fasttowin_room_pilot"
    password = settings.databasePassword
    connectTimeout = 2
    socketTimeout = 8
}

private fun Connection.backendPid() = number("SELECT pg_backend_pid()").also { check(it > 0) }

private fun Connection.number(sql: String) = createStatement().use { statement ->
    statement.executeQuery(sql).use { rows ->
        check(rows.next())
        rows.getInt(1).also { check(!rows.wasNull() && !rows.next()) }
    }
}

private fun requireDisconnected(operation: () -> Unit) {
    try {
        operation()
    } catch (e: SQLException) {
        check(e.sqlState?.startsWith("08") == true) { "Expected a connection failure" }
        return
    }
    error("Partitioned PostgreSQL path remained usable")
}

private suspend fun withFault(inject: suspend () -> Unit, restore: suspend () -> Unit, observe: suspend () -> Unit) {
    inject()
    try {
        observe()
    } finally {
        withContext(NonCancellable) { restore() }
    }
}

private suspend fun <T> verifyPending(inject: suspend () -> Unit, restore: suspend () -> Unit, expected: T, query: () -> T) {
    val reader = AtomicReference<Thread>()
    val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "fixture-capability-read").also(reader::set) }
    try {
        lateinit var response: CompletableFuture<T>
        withFault(inject, restore) {
            response = CompletableFuture.supplyAsync(query, worker)
            // Observe the network read, not just the start of the task. A worker
            // delayed before writing its command cannot prove a paused connection.
            withTimeout(2_000) {
                while (reader.get()?.stackTrace?.none { it.className == "java.net.Socket\$SocketInputStream" && it.methodName == "read" } != false) {
                    check(!response.isDone) { "The paused connection returned before its read could be observed" }
                    delay(10)
                }
            }
            requirePending(response, 400)
        }
        check(response.get(8, TimeUnit.SECONDS) == expected) { "The original connection did not recover" }
    } finally {
        worker.shutdownNow()
        // Both JDBC and Redis use bounded socket reads, so no worker survives fixture shutdown.
        check(worker.awaitTermination(9, TimeUnit.SECONDS))
    }
}

private class RedisCapabilitySocket(host: String) : Closeable {
    private val socket = Socket().apply {
        try {
            connect(InetSocketAddress(host.removeSurrounding("[", "]"), 6379), 2000)
            soTimeout = 8000
        } catch (failure: Exception) {
            close()
            throw failure
        }
    }
    private val input = socket.getInputStream().buffered()

    fun command(value: String): String {
        socket.getOutputStream().apply { write("$value\r\n".toByteArray(Charsets.US_ASCII)); flush() }
        val response = StringBuilder()
        repeat(64) {
            val byte = input.read()
            if (byte == 13) {
                check(input.read() == 10)
                return response.toString()
            }
            check(byte in 32..126) { "Invalid Redis capability response" }
            response.append(byte.toChar())
        }
        error("Redis capability response too large")
    }

    override fun close() = socket.close()
}
