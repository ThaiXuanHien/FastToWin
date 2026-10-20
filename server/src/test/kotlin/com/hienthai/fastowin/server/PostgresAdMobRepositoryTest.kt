package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.ads.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.sql.SQLException
import java.util.UUID
import kotlin.test.*

class PostgresAdMobRepositoryTest {
    @Test fun `signed SQL callback concurrent replay restart and next day preserve one grant`() = runTest {
        database { source, owner, other ->
            val f = AdMobFixture(); val quota = PostgresPlayQuotaRepository(source)
            val repository = PostgresAdMobRepository(source)
            val service = AdMobService(AdMobConfiguration(f.unit), repository, quota, f.verifier, { f.now })
            service.callback(f.signed("console-probe", unit = "0", reward = "10", item = "coins", timestamp = 1))
            assertEquals(0, count(source, "admob_rewarded_sessions", owner))
            assertEquals(0, count(source, "rewarded_ad_grants", owner))
            assertEquals(0, count(source, "daily_play_quotas", owner))
            val session = service.prepare(owner, "sql-request")
            val repeated = coroutineScope { List(8) { async(Dispatchers.Default) { service.prepare(owner, "sql-request") } }.awaitAll() }
            assertTrue(repeated.all { it.sessionId == session.sessionId })
            assertNull(service.status(other, session.sessionId))
            val query = f.signed(session.customData)
            coroutineScope { List(12) { async(Dispatchers.Default) { service.callback(query) } }.awaitAll() }
            service.callback(f.signed(session.customData, tx = "abcd0001"))
            assertEquals(12, quota.getSnapshot(owner, f.now).remainingMatches)
            assertEquals(1, count(source, "rewarded_ad_grants", owner))
            val restarted = AdMobService(AdMobConfiguration(f.unit), PostgresAdMobRepository(source), quota, f.verifier, { f.now })
            assertEquals("abcdef1234", restarted.status(owner, session.sessionId)?.providerTransactionId)
            assertEquals(RewardedAdBonusStatus.ALREADY_GRANTED, quota.grantRewardedAd(owner, RewardedAdProvider.ADMOB_ANDROID, "abcdef1234", f.now).status)
            val otherSession = service.prepare(other, "other-request")
            assertFailsWith<AdMobInvalidRequest> { service.callback(f.signed(otherSession.customData)) }
            assertEquals(10, quota.getSnapshot(other, f.now).remainingMatches)
            f.now += 86_400_000
            restarted.callback(query)
            assertEquals(10, restarted.status(owner, session.sessionId)?.quota?.remainingMatches)
            assertEquals(1, count(source, "rewarded_ad_grants", owner))
        }
    }
    @Test fun `session persistence failure rolls back receipt and daily quota then retry commits`() = runTest {
        database { source, owner, _ ->
            val f = AdMobFixture(); val quota = PostgresPlayQuotaRepository(source)
            val repository = PostgresAdMobRepository(source)
            val service = AdMobService(AdMobConfiguration(f.unit), repository, quota, f.verifier, { f.now })
            val session = service.prepare(owner, "rollback")
            val constraint = "admob_test_failure_${UUID.randomUUID().toString().replace("-", "")}"
            source.connection.use { c -> c.createStatement().use { it.execute("ALTER TABLE admob_rewarded_sessions ADD CONSTRAINT $constraint CHECK (id <> '${session.sessionId}' OR provider_transaction_id IS NULL)") } }
            try {
                assertFailsWith<SQLException> { service.callback(f.signed(session.customData)) }
                assertEquals(0, count(source, "rewarded_ad_grants", owner))
                assertEquals(0, count(source, "daily_play_quotas", owner))
                assertEquals(RewardedAdSessionStatus.PENDING, service.status(owner, session.sessionId)?.status)
            } finally { source.connection.use { c -> c.createStatement().use { it.execute("ALTER TABLE admob_rewarded_sessions DROP CONSTRAINT $constraint") } } }
            service.callback(f.signed(session.customData))
            assertEquals(12, service.status(owner, session.sessionId)?.quota?.remainingMatches)
        }
    }
    @Test fun `SQL session expiry frees bound while delayed completion credits processing date`() = runTest {
        database { source, owner, _ ->
            val f = AdMobFixture(); val quota = PostgresPlayQuotaRepository(source)
            val service = AdMobService(AdMobConfiguration(f.unit), PostgresAdMobRepository(source), quota, f.verifier, { f.now })
            val session = service.prepare(owner, "delayed"); val event = f.now
            service.prepare(owner, "cancelled-2"); service.prepare(owner, "cancelled-3")
            assertFailsWith<AdMobTooManySessions> { service.prepare(owner, "fourth") }
            f.now += 1_800_001
            assertEquals(RewardedAdSessionStatus.EXPIRED, service.status(owner, session.sessionId)?.status)
            service.prepare(owner, "fourth")
            // Cross the Bangkok midnight with a valid event from the show window.
            val nextDay = PlayQuotaRules.nextResetAtEpochMillis(event)
            f.now = nextDay + 1000
            service.callback(f.signed(session.customData, timestamp = event))
            assertEquals(10, quota.getSnapshot(owner, event).remainingMatches)
            assertEquals(12, quota.getSnapshot(owner, f.now).remainingMatches)
            assertEquals(RewardedAdSessionStatus.GRANTED, service.status(owner, session.sessionId)?.status)
        }
    }
    private fun count(source: HikariDataSource, table: String, user: String): Int = source.connection.use { c ->
        c.prepareStatement("SELECT count(*) FROM $table WHERE user_id=?").use { s ->
            s.setObject(1, UUID.fromString(user)); s.executeQuery().use { r -> r.next(); r.getInt(1) }
        }
    }
    private suspend fun database(block: suspend (HikariDataSource, String, String) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(url != null, "Requires isolated TEST_DATABASE_URL; PostgreSQL acceptance was not run")
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"; maximumPoolSize = 4
        }).use { source ->
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate()
            val identity = PostgresGuestIdentityRepository(source); val suffix = UUID.randomUUID().toString().take(8)
            val owner = identity.resolveGuest("AdMob A $suffix", null, 1_800_000_000_000L).playerId
            val other = identity.resolveGuest("AdMob B $suffix", null, 1_800_000_000_000L).playerId
            try { block(source, owner, other) } finally {
                source.connection.use { c -> c.prepareStatement("DELETE FROM users WHERE id IN (?, ?)").use {
                    it.setObject(1, UUID.fromString(owner)); it.setObject(2, UUID.fromString(other)); it.executeUpdate()
                } }
            }
        }
    }
}
