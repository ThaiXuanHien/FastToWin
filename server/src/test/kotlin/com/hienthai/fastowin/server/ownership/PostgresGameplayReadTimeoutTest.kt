package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.server.PostgresAuthRepository
import com.hienthai.fastowin.server.moderation.PostgresModerationRepository
import com.hienthai.fastowin.server.ratelimit.RedisFaultProxy
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Drop server replies on a real established JDBC connection; no server-side query cancellation can help. */
class PostgresGameplayReadTimeoutTest {
    @Test fun accessSessionReadFailsClosedWhenPostgresStopsReplying() = boundedRead { source ->
        PostgresAuthRepository(source).findActiveSession("absent-${UUID.randomUUID()}", System.currentTimeMillis())
    }

    @Test fun socketSessionReadFailsClosedWhenPostgresStopsReplying() = boundedRead { source ->
        PostgresAuthRepository(source).isActiveSessionById(UUID.randomUUID(), UUID.randomUUID(), System.currentTimeMillis())
    }

    @Test fun moderationReadFailsClosedWhenPostgresStopsReplying() = boundedRead { source ->
        PostgresModerationRepository(source).read { restriction(UUID.randomUUID().toString()).blocked }
    }

    private fun boundedRead(operation: suspend (HikariDataSource) -> Any?) {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "Isolated PostgreSQL is required")
        val target = URI(requireNotNull(url).removePrefix("jdbc:"))
        require(target.host in setOf("localhost", "127.0.0.1") && target.port in 1..65535)
        val migrationSource = PGSimpleDataSource().apply {
            setUrl(System.getenv("TEST_DATABASE_URL"))
            user = System.getenv("TEST_DATABASE_USER")
            password = System.getenv("TEST_DATABASE_PASSWORD")
        }
        Flyway.configure().dataSource(migrationSource).locations("classpath:db/migration").load().migrate()
        RedisFaultProxy("redis://${target.host}:${target.port}").use { proxy ->
            val port = URI(proxy.proxyUrl).port
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = "jdbc:postgresql://127.0.0.1:$port${target.path}?sslmode=disable"
                username = System.getenv("TEST_DATABASE_USER")
                password = System.getenv("TEST_DATABASE_PASSWORD")
                maximumPoolSize = 1
                minimumIdle = 0
                connectionTimeout = 3000
                initializationFailTimeout = -1
                addDataSourceProperty("socketTimeout", "12")
            }).use { source ->
                runBlocking { operation(source) }
                assertEquals(12_000, source.connection.use { it.networkTimeout }, "Healthy reads must restore the connection timeout")
                val worker = Executors.newSingleThreadExecutor()
                try {
                    proxy.mode = RedisFaultProxy.Mode.HOLD
                    val started = System.nanoTime()
                    val task = worker.submit<Throwable?> { runCatching { runBlocking { operation(source) } }.exceptionOrNull() }
                    try {
                        assertTrue(proxy.observed.await(2, TimeUnit.SECONDS), "The established connection must actually lose a response")
                        val failure = try { task.get(4200, TimeUnit.MILLISECONDS) }
                        catch (_: TimeoutException) { throw AssertionError("An authorization read remained blocked beyond its network budget") }
                        assertNotNull(failure, "An unavailable database cannot authorize gameplay")
                        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 4500)
                    } finally {
                        proxy.release()
                        task.get(15, TimeUnit.SECONDS)
                    }
                    runBlocking { operation(source) }
                    source.connection.use { connection ->
                        assertEquals(12_000, connection.networkTimeout, "A failed physical connection must be replaced, with no leaked timeout")
                        connection.createStatement().use { statement ->
                            statement.executeQuery("SELECT 1").use { rows -> assertTrue(rows.next()); assertEquals(1, rows.getInt(1)) }
                        }
                    }
                } finally {
                    proxy.release()
                    worker.shutdownNow()
                    assertTrue(worker.awaitTermination(15, TimeUnit.SECONDS))
                }
            }
        }
    }
}
