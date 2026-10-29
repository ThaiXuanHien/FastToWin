package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

class RefreshRecoveryPostgresTest {
    private val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 9 })
    private fun success(result: AuthResult): AuthResult.Success {
        assertTrue(result is AuthResult.Success, "Expected refresh success")
        return result
    }
    private fun failure(result: AuthResult): AuthResult.Failure {
        assertTrue(result is AuthResult.Failure, "Expected generic auth failure")
        return result
    }
    @Test fun `replay waiting on a user lock rejects at and after fixed expiry`() = runBlocking<Unit> {
        database { source ->
            val issuedAt = 1_800_000_000_000L
            val clock = AtomicLong(issuedAt)
            val auth = AuthenticationService(PostgresAuthRepository(source), PasswordHasher(1), clock::get)
            val initial = success(auth.register("wait-replay@example.com", "password123", "Wait", "android")).session
            val first = success(auth.refresh(initial.refreshToken, secret)).session
            for (afterExpiry in listOf(0L, 1L)) {
                clock.set(issuedAt + 299_999)
                val result = refreshAfterUserLockWait(source, auth, initial.userId, initial.refreshToken, clock, issuedAt + 300_000 + afterExpiry)
                assertEquals("INVALID_REFRESH_TOKEN", failure(result).code)
                assertNotNull(auth.authenticateAccessToken(first.accessToken))
            }
        }
    }
    @Test fun `fresh rotation waiting on a user lock receives full token and recovery lifetimes`() = runBlocking<Unit> {
        database { source ->
            val startedAt = 1_800_000_000_000L
            val issuedAt = startedAt + 120_000
            val clock = AtomicLong(startedAt)
            val auth = AuthenticationService(PostgresAuthRepository(source), PasswordHasher(1), clock::get)
            val initial = success(auth.register("wait-fresh@example.com", "password123", "Wait", "android")).session
            val first = success(refreshAfterUserLockWait(source, auth, initial.userId, initial.refreshToken, clock, issuedAt)).session
            assertEquals(issuedAt + 900_000, first.accessExpiresAtEpochMillis)
            assertEquals(issuedAt + 2_592_000_000, first.refreshExpiresAtEpochMillis)
            source.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT issued_at, recovery_expires_at, metadata_expires_at FROM auth_refresh_recoveries").use {
                        assertTrue(it.next())
                        assertEquals(issuedAt, it.getTimestamp(1).time)
                        assertEquals(issuedAt + 300_000, it.getTimestamp(2).time)
                        assertEquals(first.refreshExpiresAtEpochMillis, it.getTimestamp(3).time)
                    }
                }
            }
            clock.set(issuedAt + 299_999)
            assertEquals(first, success(auth.refresh(initial.refreshToken, secret)).session)
            clock.incrementAndGet()
            failure(auth.refresh(initial.refreshToken, secret))
        }
    }
    @Test fun `source session waiting on a user lock cannot rotate after session expiry`() = runBlocking<Unit> {
        database { source ->
            val clock = AtomicLong(1_800_000_000_000L)
            val auth = AuthenticationService(PostgresAuthRepository(source), PasswordHasher(1), clock::get)
            val initial = success(auth.register("wait-source@example.com", "password123", "Wait", "android")).session
            clock.set(initial.refreshExpiresAtEpochMillis - 1)
            val result = refreshAfterUserLockWait(source, auth, initial.userId, initial.refreshToken, clock, initial.refreshExpiresAtEpochMillis)
            assertEquals("INVALID_REFRESH_TOKEN", failure(result).code)
            assertEquals(0, count(source, "SELECT count(*) FROM auth_refresh_recoveries"))
        }
    }
    @Test fun `durable duplicate rotation uses the real session and survives repository restart`() = runBlocking<Unit> {
        database { source ->
            val now = 1_800_000_000_000L
            val auth = AuthenticationService(PostgresAuthRepository(source), PasswordHasher(1), { now })
            val initial = success(auth.register("durable@example.com", "password123", "Durable", "android")).session
            val results = coroutineScope { (1..8).map { async(Dispatchers.Default) { auth.refresh(initial.refreshToken, secret) } }.awaitAll() }
            val first = success(results.first()).session
            results.forEach { assertTrue(first == success(it).session) }
            val restarted = AuthenticationService(PostgresAuthRepository(source), PasswordHasher(1), { now + 1 })
            assertTrue(first == success(restarted.refresh(initial.refreshToken, secret)).session)
            source.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM auth_refresh_recoveries r JOIN sessions s ON s.id=r.session_id WHERE r.successor_refresh_hash=s.refresh_token_hash AND r.successor_access_hash=s.access_token_hash").use {
                        assertTrue(it.next()); assertEquals(1, it.getInt(1))
                    }
                }
            }
            restarted.logout(initial.refreshToken, secret)
            assertNull(restarted.authenticateAccessToken(first.accessToken))
        }
    }
    @Test fun `fixed boundary and bounded ciphertext cleanup retain only logout mapping`() = runBlocking<Unit> {
        database { source ->
            var now = 1_800_000_000_000L
            val repository = PostgresAuthRepository(source)
            val auth = AuthenticationService(repository, PasswordHasher(1), { now })
            val sessions = (1..3).map {
                val initial = success(auth.register("cleanup$it@example.com", "password123", "Cleanup", "android")).session
                initial to success(auth.refresh(initial.refreshToken, secret)).session
            }
            now += 299_999
            assertEquals(sessions.first().second, success(auth.refresh(sessions.first().first.refreshToken, secret)).session)
            now++
            failure(auth.refresh(sessions.first().first.refreshToken, secret))
            assertEquals(1, repository.cleanupRefreshRecoveries(now, 1))
            assertEquals(2, count(source, "SELECT count(*) FROM auth_refresh_recoveries WHERE ciphertext IS NOT NULL"))
            assertEquals(2, repository.cleanupRefreshRecoveries(now, 100))
            assertEquals(3, count(source, "SELECT count(*) FROM auth_refresh_recoveries WHERE ciphertext IS NULL AND nonce IS NULL"))
            for ((initial, successor) in sessions) {
                failure(auth.refresh(initial.refreshToken, secret))
                auth.logout(initial.refreshToken, secret)
                assertNull(auth.authenticateAccessToken(successor.accessToken))
            }
            assertEquals(0, count(source, "SELECT count(*) FROM auth_refresh_recoveries"))
        }
    }
    @Test fun `receipt insert failure rolls token rotation back atomically`() = runBlocking<Unit> {
        database { source ->
            val now = 1_800_000_000_000L
            val auth = AuthenticationService(PostgresAuthRepository(source), PasswordHasher(1), { now })
            val initial = success(auth.register("rollback@example.com", "password123", "Rollback", "android")).session
            sql(source, "CREATE FUNCTION reject_receipt() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''intentional receipt failure''; END'")
            sql(source, "CREATE TRIGGER reject_receipt BEFORE INSERT ON auth_refresh_recoveries FOR EACH ROW EXECUTE FUNCTION reject_receipt()")
            try {
                auth.refresh(initial.refreshToken, secret)
                fail("Receipt persistence failure must abort refresh")
            } catch (_: java.sql.SQLException) { /* Expected transaction rollback. */ }
            assertNotNull(auth.authenticateAccessToken(initial.accessToken))
            assertEquals(0, count(source, "SELECT count(*) FROM auth_refresh_recoveries"))
            sql(source, "DROP TRIGGER reject_receipt ON auth_refresh_recoveries")
            success(auth.refresh(initial.refreshToken, secret))
        }
    }
    @Test fun `all revocation paths and inactive account fence durable recovery`() = runBlocking<Unit> {
        database { source ->
            val now = 1_800_000_000_000L
            val repository = PostgresAuthRepository(source)
            val auth = AuthenticationService(repository, PasswordHasher(1), { now })
            for (action in listOf("legacy", "logout", "one", "all", "login", "password", "reset", "delete", "inactive")) {
                val email = "$action@example.com"
                val initial = success(auth.register(email, "password123", "Revocation", "android")).session
                val first = success(auth.refresh(initial.refreshToken, secret)).session
                var surviving: com.hienthai.fastowin.protocol.AuthSessionResponse? = null
                when (action) {
                    "legacy" -> surviving = success(auth.refresh(first.refreshToken)).session
                    "logout" -> auth.logout(first.refreshToken)
                    "one" -> repository.revokeSessionById(UUID.fromString(first.userId), assertNotNull(auth.authenticateAccessToken(first.accessToken)?.sessionId), now)
                    "all" -> repository.revokeAllSessions(UUID.fromString(first.userId), now)
                    "login" -> surviving = success(auth.login(email, "password123", "ios")).session
                    "password" -> assertIs<AccountActionResult.Success>(auth.changePassword(first.accessToken, "password123", "new-password123"))
                    "reset" -> {
                        val reset = assertIs<AccountActionResult.Success>(auth.requestPasswordReset(email))
                        assertIs<AccountActionResult.Success>(auth.resetPassword(email, assertNotNull(reset.resetToken), "new-password123"))
                    }
                    "delete" -> assertIs<AccountActionResult.Success>(auth.deleteAccount(first.accessToken, "password123"))
                    "inactive" -> source.connection.use { connection ->
                        connection.prepareStatement("UPDATE users SET status = 'DELETED' WHERE id = ?").use {
                            it.setObject(1, UUID.fromString(first.userId)); it.executeUpdate()
                        }
                    }
                }
                failure(auth.refresh(initial.refreshToken, secret))
                auth.logout(initial.refreshToken, secret)
                assertNull(auth.authenticateAccessToken(first.accessToken))
                surviving?.let { assertNotNull(auth.authenticateAccessToken(it.accessToken)) }
                assertEquals(0, count(source, "SELECT count(*) FROM auth_refresh_recoveries"))
            }
        }
    }
    @Test fun `tampered durable receipt fails closed without changing successor`() = runBlocking<Unit> {
        database { source ->
            val now = 1_800_000_000_000L
            val auth = AuthenticationService(PostgresAuthRepository(source), PasswordHasher(1), { now })
            val initial = success(auth.register("tamper@example.com", "password123", "Tamper", "android")).session
            val first = success(auth.refresh(initial.refreshToken, secret)).session
            sql(source, "UPDATE auth_refresh_recoveries SET ciphertext = set_byte(ciphertext, 0, get_byte(ciphertext, 0) # 1)")
            failure(auth.refresh(initial.refreshToken, secret))
            assertNotNull(auth.authenticateAccessToken(first.accessToken))
            auth.logout(initial.refreshToken, secret)
            assertNull(auth.authenticateAccessToken(first.accessToken))
        }
    }
    private fun sql(source: HikariDataSource, sql: String) = source.connection.use { it.createStatement().use { s -> s.execute(sql) } }
    private suspend fun refreshAfterUserLockWait(
        source: HikariDataSource, auth: AuthenticationService, userId: String, token: String,
        clock: AtomicLong, releaseAtMillis: Long
    ): AuthResult = coroutineScope {
        source.connection.use { blocker ->
            blocker.autoCommit = false
            val blockerPid = blocker.createStatement().use { statement ->
                statement.executeQuery("SELECT pg_backend_pid()").use { assertTrue(it.next()); it.getInt(1) }
            }
            blocker.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE").use {
                it.setObject(1, UUID.fromString(userId)); it.executeQuery().use { rows -> assertTrue(rows.next()) }
            }
            val pending = async(Dispatchers.Default) { auth.refresh(token, secret) }
            try {
                // Observe the actual PostgreSQL lock wait before advancing time; no timing sleep substitutes for the barrier.
                withTimeout(5_000) {
                    while (count(source, "SELECT count(*) FROM pg_stat_activity WHERE $blockerPid = ANY(pg_blocking_pids(pid))") == 0) delay(10)
                }
                clock.set(releaseAtMillis)
                blocker.commit()
                pending.await()
            } finally { blocker.rollback() }
        }
    }
    private fun count(source: HikariDataSource, sql: String): Int = source.connection.use {
        it.createStatement().use { s -> s.executeQuery(sql).use { rows -> assertTrue(rows.next()); rows.getInt(1) } }
    }
    private suspend fun database(block: suspend (HikariDataSource) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "Local PostgreSQL required")
        val schema = "refresh_recovery_${UUID.randomUUID().toString().replace("-", "")}"
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize = 8
            connectionInitSql = "SET statement_timeout = '15s'"
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try {
                HikariDataSource(config().apply { this.schema = schema }).use { source ->
                    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                    block(source)
                }
            } finally { setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }
}
