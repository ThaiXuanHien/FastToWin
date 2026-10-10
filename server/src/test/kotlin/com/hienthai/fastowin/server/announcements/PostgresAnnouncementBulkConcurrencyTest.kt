package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.server.AuthResult
import com.hienthai.fastowin.server.AuthenticationService
import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.PostgresAuthRepository
import com.hienthai.fastowin.server.PostgresNotificationRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PostgresAnnouncementBulkConcurrencyTest {
    @Test fun `HTTP read all overlaps legacy read all`() = overlap(false, false)
    @Test fun `HTTP read all overlaps legacy dismiss all`() = overlap(false, true)
    @Test fun `HTTP dismiss all overlaps legacy read all`() = overlap(true, false)
    @Test fun `HTTP dismiss all overlaps legacy dismiss all`() = overlap(true, true)

    private fun overlap(httpDismiss: Boolean, legacyDismiss: Boolean) = runBlocking {
        val url = requireNotNull(System.getenv("TEST_DATABASE_URL")) { "Real PostgreSQL is required" }
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER")
            password = System.getenv("TEST_DATABASE_PASSWORD")
            maximumPoolSize = 3 // Two mutation connections plus read-only lock observation.
        }).use { ds ->
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
            val auth = AuthenticationService(PostgresAuthRepository(ds), PasswordHasher(iterations = 1_000))
            val user = assertIs<AuthResult.Success>(auth.register("bulk-${UUID.randomUUID()}@example.com", "password123", "Player", "android")).session.userId
            val suffix = UUID.randomUUID().toString().substring(9)
            val ids = listOf("00000001-$suffix", "00000002-$suffix")
            val repo = PostgresNotificationRepository(ds)
            val locked = CountDownLatch(1)
            val resume = CountDownLatch(1)
            var http: CompletableFuture<*>? = null
            var legacy: CompletableFuture<*>? = null
            try {
                repo.announcements.transaction {
                    ids.forEachIndexed { index, id ->
                        val definition = AnnouncementRepositoryTest().fixtureDefinition(id)
                        putDefinition(definition)
                        putRecipient(AnnouncementRecipient(id, user, storedAtEpochMillis = 100L + index))
                        notifications.create(user, listOf(definition.notification("en", 100L + index)))
                    }
                }
                // Pause only after the first REAL receipt-lock query has executed. On the old
                // HTTP path B is held; on the fixed bulk path A and B are held. No SQL is mocked.
                val pid = CompletableFuture<Int>()
                val paused = object : DataSource by ds {
                    override fun getConnection(): Connection = pauseAfterFirstReceiptLock(ds.connection, pid, locked, resume)
                }
                val service = AnnouncementService(PostgresAnnouncementRepository(paused), null, nowMillis = { 200 })
                http = CompletableFuture.runAsync { runBlocking {
                    val result = if (httpDismiss) service.dismiss(user, null) else service.markRead(user, null)
                    assertEquals("2", result.affected)
                } }
                assertTrue(locked.await(10, TimeUnit.SECONDS), "HTTP must acquire its first receipt lock")
                legacy = CompletableFuture.runAsync { runBlocking {
                    if (legacyDismiss) repo.dismissNotifications(user, null, 300) else repo.markNotificationsRead(user, null, 300)
                } }
                assertTrue(waitUntilBlocked(ds, pid.get(5, TimeUnit.SECONDS)), "legacy bulk must overlap on a real PostgreSQL receipt lock")
                resume.countDown()
                // Old code forms B -> A / A -> B here: PostgreSQL detects a deadlock.
                val outcomes = listOf(http, legacy).map { runCatching { it.get(15, TimeUnit.SECONDS) } }
                assertTrue(outcomes.all { it.isSuccess }, "Both protocols must commit without deadlock: ${outcomes.map { it.exceptionOrNull() }}")
                repo.announcements.transaction {
                    for (id in ids) {
                        val receipt = recipient(id, user)!!
                        assertEquals(if (httpDismiss || legacyDismiss) (if (httpDismiss) 200L else 300L) else null, receipt.dismissedAtEpochMillis)
                        assertEquals(if (httpDismiss) null else 200L, receipt.readAtEpochMillis)
                    }
                    assertEquals(if (httpDismiss || legacyDismiss) 0L else 2L, notifications.count(user))
                    if (!httpDismiss && !legacyDismiss) assertTrue(notifications.load(user).all { it.isRead })
                }
            } finally {
                resume.countDown()
                listOfNotNull(http, legacy).forEach { runCatching { it.get(20, TimeUnit.SECONDS) } }
                ds.connection.use { c ->
                    c.prepareStatement("DELETE FROM users WHERE id=?").use { it.setObject(1, UUID.fromString(user)); it.executeUpdate() }
                    ids.forEach { id ->
                        for (table in listOf("admin_announcement_recipients", "admin_announcements")) {
                            c.prepareStatement("DELETE FROM $table WHERE ${if (table == "admin_announcements") "id" else "announcement_id"}=?").use {
                                it.setObject(1, UUID.fromString(id)); it.executeUpdate()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pauseAfterFirstReceiptLock(connection: Connection, pid: CompletableFuture<Int>, locked: CountDownLatch, resume: CountDownLatch): Connection {
        connection.createStatement().use { statement ->
            statement.execute("SET statement_timeout='12s'")
            statement.executeQuery("SELECT pg_backend_pid()").use { it.next(); pid.complete(it.getInt(1)) }
        }
        val first = AtomicBoolean(true)
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
            try {
                val result = method.invoke(connection, *(args ?: emptyArray()))
                val sql = args?.firstOrNull() as? String ?: ""
                if (method.name == "prepareStatement" && sql.contains("SELECT announcement_id FROM admin_announcement_recipients") && sql.contains("FOR UPDATE")) {
                    val statement = result as PreparedStatement
                    Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PreparedStatement::class.java)) { _, called, parameters ->
                        try {
                            val value = called.invoke(statement, *(parameters ?: emptyArray()))
                            if (called.name == "executeQuery" && first.compareAndSet(true, false)) {
                                locked.countDown()
                                check(resume.await(10, TimeUnit.SECONDS)) { "Test did not release HTTP transaction" }
                            }
                            value
                        } catch (error: InvocationTargetException) { throw error.targetException }
                    }
                } else result
            } catch (error: InvocationTargetException) { throw error.targetException }
        } as Connection
    }

    private fun waitUntilBlocked(ds: DataSource, pid: Int): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            ds.connection.use { c ->
                c.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)) AND query LIKE '%admin_announcement_recipients%')").use {
                    it.setInt(1, pid)
                    it.executeQuery().use { result -> result.next(); if (result.getBoolean(1)) return true }
                }
            }
            Thread.sleep(20)
        }
        return false
    }
}
