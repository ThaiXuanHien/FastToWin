package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.server.moderation.ModerationRestrictionView
import com.hienthai.fastowin.server.moderation.PostgresModerationRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource
import kotlin.test.*

/** Deterministic transaction schedules using production mutation paths, never synthetic revocation SQL. */
class PostgresRefreshRecoveryTest {
    private val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 17 })
    private val otherSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 18 })
    private val now = 1_800_000_000_000L

    @TestFactory fun `revocation and recovery serialize in both orders`() =
        listOf("logout", "pendingLogout", "one", "all", "login", "block", "password", "reset", "delete").flatMap { action ->
            listOf(true, false).map { revokeFirst ->
                DynamicTest.dynamicTest("$action ${if (revokeFirst) "commits before recovery" else "waits for recovery"}") {
                    runBlocking { database { source ->
                        val gate = CommitGate(source)
                        val auth = AuthenticationService(PostgresAuthRepository(gate), PasswordHasher(1), { now })
                        val initial = success(auth.register("$action-$revokeFirst@example.com", "password123", "Race", "android"))
                        val successor = success(auth.refresh(initial.refreshToken, secret))
                        val sessionId = assertNotNull(auth.authenticateAccessToken(successor.accessToken)?.sessionId)
                        val reset = if (action == "reset") assertNotNull(assertIs<AccountActionResult.Success>(auth.requestPasswordReset("$action-$revokeFirst@example.com")).resetToken) else null
                        suspend fun revoke() {
                            when (action) {
                                "logout" -> auth.logout(successor.refreshToken)
                                "pendingLogout" -> auth.logout(initial.refreshToken, secret)
                                "one" -> assertIs<AccountActionResult.Success>(auth.revokeSession(successor.accessToken, sessionId.toString()))
                                "all" -> assertIs<AccountActionResult.Success>(auth.revokeAllSessions(successor.accessToken))
                                "login" -> success(auth.login("$action-$revokeFirst@example.com", "password123", "ios"))
                                "block" -> PostgresModerationRepository(gate).transaction { putRestriction(ModerationRestrictionView(initial.userId, 1, true), now) }
                                "password" -> assertIs<AccountActionResult.Success>(auth.changePassword(successor.accessToken, "password123", "new-password123"))
                                "reset" -> assertIs<AccountActionResult.Success>(auth.resetPassword("$action-$revokeFirst@example.com", assertNotNull(reset), "new-password123"))
                                "delete" -> assertIs<AccountActionResult.Success>(auth.deleteAccount(successor.accessToken, "password123"))
                            }
                        }
                        gate.arm()
                        coroutineScope {
                            val first = async(Dispatchers.Default) { if (revokeFirst) { revoke(); null } else auth.refresh(initial.refreshToken, secret) }
                            try {
                                gate.awaitEntered()
                                val second = async(Dispatchers.Default) { if (revokeFirst) auth.refresh(initial.refreshToken, secret) else { revoke(); null } }
                                awaitBlocked(source, gate.pid)
                                gate.release()
                                if (revokeFirst) { first.await(); assertIs<AuthResult.Failure>(second.await()) }
                                else { assertEquals(successor, success(assertNotNull(first.await()))); second.await() }
                            } finally { gate.release() }
                        }
                        assertNull(auth.authenticateAccessToken(successor.accessToken))
                        assertIs<AuthResult.Failure>(auth.refresh(initial.refreshToken, secret))
                        assertEquals(0, count(source, "SELECT count(*) FROM auth_refresh_recoveries"))
                    } }
                }
            }
        }

    @TestFactory fun `fresh rotations and replay use independent connections and one generation`() =
        listOf("same", "different", "legacyNext", "recoverableNext").map { schedule ->
            DynamicTest.dynamicTest(schedule) { runBlocking { database { source ->
                val gate = CommitGate(source)
                val auth = AuthenticationService(PostgresAuthRepository(gate), PasswordHasher(1), { now })
                val initial = success(auth.register("$schedule@example.com", "password123", "Race", "android"))
                val issued = if (schedule.endsWith("Next")) success(auth.refresh(initial.refreshToken, secret)) else null
                gate.arm()
                coroutineScope {
                    val first = async(Dispatchers.Default) {
                        if (issued == null) auth.refresh(initial.refreshToken, secret)
                        else auth.refresh(issued.refreshToken, if (schedule == "legacyNext") null else otherSecret)
                    }
                    try {
                        gate.awaitEntered()
                        val second = async(Dispatchers.Default) { auth.refresh(initial.refreshToken, if (schedule == "different") otherSecret else secret) }
                        awaitBlocked(source, gate.pid)
                        gate.release()
                        val result = success(first.await())
                        if (schedule == "same") assertEquals(result, success(second.await()))
                        else assertIs<AuthResult.Failure>(second.await())
                        assertNotNull(auth.authenticateAccessToken(result.accessToken))
                        assertEquals(if (schedule == "legacyNext") 0 else 1,
                            count(source, "SELECT count(*) FROM auth_refresh_recoveries r JOIN sessions s ON s.id=r.session_id WHERE r.successor_refresh_hash=s.refresh_token_hash AND r.successor_access_hash=s.access_token_hash"))
                        auth.logout(initial.refreshToken, secret)
                        if (issued != null) assertNotNull(auth.authenticateAccessToken(result.accessToken))
                        assertEquals(if (schedule == "legacyNext" || schedule == "same" || schedule == "different") 0 else 1,
                            count(source, "SELECT count(*) FROM auth_refresh_recoveries"))
                        assertEquals(1, count(source, "SELECT count(*) FROM sessions"))
                    } finally { gate.release() }
                }
            } } }
        }

    @TestFactory fun `replay completing before next rotation cannot survive that rotation`() = listOf(false, true).map { recoverable ->
        DynamicTest.dynamicTest("replay then ${if (recoverable) "recoverable" else "legacy"} rotation") { runBlocking { database { source ->
            val gate = CommitGate(source)
            val auth = AuthenticationService(PostgresAuthRepository(gate), PasswordHasher(1), { now })
            val initial = success(auth.register("replay-next@example.com", "password123", "Race", "android"))
            val issued = success(auth.refresh(initial.refreshToken, secret))
            gate.arm()
            coroutineScope {
                val replay = async(Dispatchers.Default) { auth.refresh(initial.refreshToken, secret) }
                try {
                    gate.awaitEntered()
                    val next = async(Dispatchers.Default) { auth.refresh(issued.refreshToken, if (recoverable) otherSecret else null) }
                    awaitBlocked(source, gate.pid)
                    gate.release()
                    assertEquals(issued, success(replay.await()))
                    val advanced = success(next.await())
                    assertIs<AuthResult.Failure>(auth.refresh(initial.refreshToken, secret))
                    auth.logout(initial.refreshToken, secret)
                    assertNull(auth.authenticateAccessToken(issued.accessToken))
                    assertNotNull(auth.authenticateAccessToken(advanced.accessToken))
                    assertEquals(if (recoverable) 1 else 0, count(source, "SELECT count(*) FROM auth_refresh_recoveries"))
                } finally { gate.release() }
            }
        } } }
    }

    @Test fun `receipt dump has no plaintext and metadata is deleted exactly at successor expiry`() = runBlocking<Unit> {
        database { source ->
            val repository = PostgresAuthRepository(source)
            val auth = AuthenticationService(repository, PasswordHasher(1), { now })
            val initial = success(auth.register("privacy@example.com", "password123", "Privacy", "android"))
            val successor = success(auth.refresh(initial.refreshToken, secret))
            source.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT row_to_json(r)::text FROM auth_refresh_recoveries r").use {
                        assertTrue(it.next())
                        val dump = it.getString(1)
                        for (credential in listOf(initial.accessToken, initial.refreshToken, successor.accessToken, successor.refreshToken, secret))
                            assertFalse(dump.contains(credential), "Receipt contains plaintext credential")
                        assertFalse(dump.contains("accessToken"))
                    }
                }
            }
            assertEquals(1, repository.cleanupRefreshRecoveries(now + 300_000))
            assertEquals(0, repository.cleanupRefreshRecoveries(successor.refreshExpiresAtEpochMillis - 1))
            assertEquals(1, count(source, "SELECT count(*) FROM auth_refresh_recoveries WHERE ciphertext IS NULL"))
            assertEquals(1, repository.cleanupRefreshRecoveries(successor.refreshExpiresAtEpochMillis))
            assertEquals(0, count(source, "SELECT count(*) FROM auth_refresh_recoveries"))
        }
    }

    @TestFactory fun `durable authentication tag and bound metadata tampering fail closed`() =
        listOf("ciphertext = set_byte(ciphertext, octet_length(ciphertext)-1, get_byte(ciphertext, octet_length(ciphertext)-1) # 1)",
            "nonce = set_byte(nonce, 0, get_byte(nonce, 0) # 1)",
            "issued_at = issued_at + interval '1 second', recovery_expires_at = recovery_expires_at + interval '1 second'",
            "session_id = (SELECT id FROM sessions WHERE id <> auth_refresh_recoveries.session_id LIMIT 1)").mapIndexed { index, mutation ->
            DynamicTest.dynamicTest("tampering $index") { runBlocking { database { source ->
                val auth = AuthenticationService(PostgresAuthRepository(source), PasswordHasher(1), { now })
                val initial = success(auth.register("tamper@example.com", "password123", "Tamper", "android"))
                success(auth.register("other@example.com", "password123", "Other", "android"))
                val successor = success(auth.refresh(initial.refreshToken, secret))
                source.connection.use { it.createStatement().use { s -> s.executeUpdate("UPDATE auth_refresh_recoveries SET $mutation") } }
                assertIs<AuthResult.Failure>(auth.refresh(initial.refreshToken, secret))
                assertNotNull(auth.authenticateAccessToken(successor.accessToken))
            } } }
        }

    private fun success(result: AuthResult) = assertIs<AuthResult.Success>(result).session
    private suspend fun awaitBlocked(source: DataSource, pid: Int) = withTimeout(5_000) {
        while (count(source, "SELECT count(*) FROM pg_stat_activity WHERE $pid = ANY(pg_blocking_pids(pid))") == 0) delay(10)
    }
    private fun count(source: DataSource, sql: String): Int = source.connection.use {
        it.createStatement().use { s -> s.executeQuery(sql).use { rows -> assertTrue(rows.next()); rows.getInt(1) } }
    }
    private class CommitGate(private val source: DataSource) : DataSource by source {
        private val armed = AtomicBoolean()
        private val entered = CountDownLatch(1)
        private val proceed = CountDownLatch(1)
        @Volatile var pid = 0; private set
        fun arm() { armed.set(true) }
        suspend fun awaitEntered() = withContext(Dispatchers.IO) { check(entered.await(5, TimeUnit.SECONDS)) { "Commit gate not reached" } }
        fun release() { proceed.countDown() }
        override fun getConnection(): Connection {
            val actual = source.connection
            return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                if (method.name == "commit" && armed.compareAndSet(true, false)) {
                    pid = actual.createStatement().use { s -> s.executeQuery("SELECT pg_backend_pid()").use { it.next(); it.getInt(1) } }
                    entered.countDown()
                    check(proceed.await(10, TimeUnit.SECONDS)) { "Commit gate release timed out" }
                }
                try { method.invoke(actual, *(args ?: emptyArray())) }
                catch (error: InvocationTargetException) { throw error.targetException }
            } as Connection
        }
    }
    private suspend fun database(block: suspend (HikariDataSource) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "Local PostgreSQL required")
        val schema = "recovery_acceptance_${UUID.randomUUID().toString().replace("-", "")}"
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD")
            maximumPoolSize = 8; connectionInitSql = "SET statement_timeout = '15s'"
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try { HikariDataSource(config().apply { this.schema = schema }).use { source ->
                Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                block(source)
            } } finally { setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }
}
