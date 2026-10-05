package com.hienthai.fastowin.server.analytics

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.sun.net.httpserver.HttpServer
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.*
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.InetSocketAddress
import java.net.URI
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.*

class PostgresFinancialSyncTest {
    @Test fun verifiedGrantCapturesAnonymousOrderAndLateRefundSurvivesDeletion() = runTest { database {
        val account = PostgresAnalyticsFactsTest.account()
        assertTrue(PostgresAuthRepository(db).createAccount(account))
        val userId = account.userId.toString()
        assertEquals(StorePurchaseStatus.GRANTED, purchase(userId).status)
        assertEquals(StorePurchaseStatus.ALREADY_GRANTED, purchase(userId).status)
        assertEquals(1, count("store_financial_orders"))
        assertEquals(1, count("wallet_transactions"))
        assertTrue(sync.syncNext(provider, START, NOW))
        assertEquals("9007199254740993", text("SELECT amount_minor::text FROM analytics_financial_events"))
        val subject = text("SELECT subject_key::text FROM analytics_subject_links")
        assertEquals(subject, text("SELECT subject_key::text FROM analytics_financial_events"))
        assertTrue(PostgresAuthRepository(db).deleteAccount(account.userId))
        assertEquals(0, count("analytics_subject_links"))
        response = GoogleOrdersFinancialSourceTest.orderJson("USD", "90071992547409", "930000000", "PARTIALLY_REFUNDED",
            GoogleOrdersFinancialSourceTest.partial("2", "2026-10-05T01:00:00Z")).replace("2026-10-04T01:00:00Z", "2026-10-05T01:00:00Z")
        assertTrue(sync.syncNext(provider, START, NOW.plusSeconds(3601)))
        assertEquals(2, count("analytics_financial_events"))
        assertEquals(subject, text("SELECT subject_key::text FROM analytics_financial_events WHERE event_type='REFUND'"))
        assertEquals(0, count("analytics_subject_links"))
        assertEquals("PARTIAL", text("SELECT status FROM analytics_source_coverage WHERE source='GOOGLE_PLAY' AND environment='LIVE'"))
    } }

    @Test fun retriesAndConflictingCorrectionsNeverDoubleCountOrOverwriteFacts() = runTest { database {
        val user = PostgresGuestIdentityRepository(db).resolveGuest("Buyer", null, NOW.toEpochMilli())
        purchase(user.playerId)
        response = GoogleOrdersFinancialSourceTest.orderJson("USD", "10", "0", "PARTIALLY_REFUNDED", GoogleOrdersFinancialSourceTest.partial("2", "2026-10-02T01:00:00Z"))
        assertTrue(sync.syncNext(provider, START, NOW))
        assertTrue(sync.syncNext(provider, START, NOW.plusSeconds(3601)))
        assertEquals(2, count("analytics_financial_events"))
        response = response.replace("\"units\":\"2\"", "\"units\":\"3\"").replace("2026-10-04T01:00:00Z", "2026-10-05T01:00:00Z")
        assertTrue(sync.syncNext(provider, START, NOW.plusSeconds(7202)))
        assertEquals("200", text("SELECT amount_minor::text FROM analytics_financial_events WHERE event_type='REFUND'"))
        assertEquals("CONFLICT", text("SELECT sync_status FROM store_financial_orders"))
        assertEquals("PARTIAL", text("SELECT status FROM analytics_source_coverage WHERE source='GOOGLE_PLAY' AND environment='LIVE'"))
    } }

    @Test fun configuredRunnerResumesDurableDueOrderAndDisabledStartupClearsCoverage() = runTest { database {
        val user = PostgresGuestIdentityRepository(db).resolveGuest("Buyer", null, NOW.toEpochMilli())
        purchase(user.playerId)
        val runner = FinancialSyncService(sync, provider, START)
        runner.initialize()
        assertEquals(1, runner.runBatch(NOW))
        assertEquals(0, runner.runBatch(NOW.plusSeconds(1)))
        val restarted = FinancialSyncService(PostgresFinancialSyncRepository(db), provider, START)
        assertEquals(1, restarted.runBatch(NOW.plusSeconds(3601)))
        assertEquals(1, count("analytics_financial_events"))
        FinancialSyncService(sync, null, null).initialize()
        assertEquals("UNAVAILABLE", text("SELECT status FROM analytics_source_coverage WHERE source='GOOGLE_PLAY' AND environment='LIVE'"))
        assertNull(configuredFinancialSource(emptyMap()))
        assertNull(configuredFinancialSource(mapOf("FINANCIAL_GOOGLE_ENABLED" to "true")))
    } }

    @Test fun captureFailureRollsBackGrantAndLateSourceFailureNeverChangesWallet() = runTest { database {
        val user = PostgresGuestIdentityRepository(db).resolveGuest("Buyer", null, NOW.toEpochMilli())
        val before = count("player_stats")
        sql("CREATE FUNCTION reject_order() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'order rejected'; END ${'$'}${'$'}")
        sql("CREATE CONSTRAINT TRIGGER reject_order AFTER INSERT ON store_financial_orders DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reject_order()")
        assertFailsWith<SQLException> { purchase(user.playerId) }
        assertEquals(0, count("store_purchases")); assertEquals(0, count("wallet_transactions")); assertEquals(0, count("store_financial_orders"))
        assertEquals(before, count("player_stats"))
        sql("DROP TRIGGER reject_order ON store_financial_orders")
        assertEquals(StorePurchaseStatus.GRANTED, purchase(user.playerId).status)
        val funded = text("SELECT gems FROM player_stats WHERE user_id='${user.playerId}'")
        code = 403; assertTrue(sync.syncNext(provider, START, NOW))
        assertEquals("UNAVAILABLE", text("SELECT status FROM analytics_source_coverage WHERE source='GOOGLE_PLAY' AND environment='LIVE'"))
        assertEquals(funded, text("SELECT gems FROM player_stats WHERE user_id='${user.playerId}'"))
        code = 200; assertTrue(sync.syncNext(provider, START, NOW.plusSeconds(3601)))
        val freshness = text("SELECT freshness::text FROM analytics_source_coverage WHERE source='GOOGLE_PLAY' AND environment='LIVE'")
        code = 503; assertTrue(sync.syncNext(provider, START, NOW.plusSeconds(7202)))
        assertEquals("PARTIAL", text("SELECT status FROM analytics_source_coverage WHERE source='GOOGLE_PLAY' AND environment='LIVE'"))
        assertEquals(freshness, text("SELECT freshness::text FROM analytics_source_coverage WHERE source='GOOGLE_PLAY' AND environment='LIVE'"))
        assertEquals(1, count("analytics_financial_events"))
    } }

    @Test fun concurrentWorkersSkipLockedOrderAndCommitOneSnapshot() = runTest { database {
        val user = PostgresGuestIdentityRepository(db).resolveGuest("Buyer", null, NOW.toEpochMilli())
        purchase(user.playerId)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        orderRequest = { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        coroutineScope {
            val first = async(Dispatchers.IO) { sync.syncNext(provider, START, NOW) }
            withContext(Dispatchers.IO) { assertTrue(entered.await(10, TimeUnit.SECONDS)) }
            try { assertFalse(withContext(Dispatchers.IO) { PostgresFinancialSyncRepository(db).syncNext(provider, START, NOW) }) }
            finally { release.countDown() }
            assertTrue(first.await())
        }
        assertEquals(1, count("analytics_financial_events"))
    } }

    @Test fun cumulativeFullRefundAndStaleMissingOrEqualTimeConflictRemainExact() = runTest { database {
        val user = PostgresGuestIdentityRepository(db).resolveGuest("Buyer", null, NOW.toEpochMilli())
        purchase(user.playerId)
        val partial = GoogleOrdersFinancialSourceTest.partial("2", "2026-10-02T01:00:00Z")
        response = GoogleOrdersFinancialSourceTest.orderJson("USD", "10", "0", "PARTIALLY_REFUNDED", partial)
        sync.syncNext(provider, START, NOW)
        val prior = response
        response = GoogleOrdersFinancialSourceTest.orderJson("USD", "10", "0", "REFUNDED", partial +
            ",\"refundEvent\":{\"eventTime\":\"2026-10-05T01:00:00Z\",\"refundDetails\":{\"total\":${GoogleOrdersFinancialSourceTest.money("USD", "10")}}}")
            .replace("2026-10-04T01:00:00Z", "2026-10-05T01:00:00Z")
        sync.syncNext(provider, START, NOW.plusSeconds(3601))
        assertEquals("1000", text("SELECT SUM(amount_minor)::text FROM analytics_financial_events WHERE event_type='REFUND'"))
        assertEquals(3, count("analytics_financial_events"))
        response = prior
        sync.syncNext(provider, START, NOW.plusSeconds(7202))
        assertEquals("CONFLICT", text("SELECT sync_status FROM store_financial_orders"))
        assertEquals(3, count("analytics_financial_events"))
        response = prior.replace("2026-10-04T01:00:00Z", "2026-10-05T01:00:00Z")
        sync.syncNext(provider, START, NOW.plusSeconds(10803))
        assertEquals("CONFLICT", text("SELECT sync_status FROM store_financial_orders"))
        assertEquals("1000", text("SELECT SUM(amount_minor)::text FROM analytics_financial_events WHERE event_type='REFUND'"))
    } }

    @Test fun nanosecondProviderTimesSurviveDatabaseReplayWithoutFalseConflict() = runTest { database {
        val user = PostgresGuestIdentityRepository(db).resolveGuest("Buyer", null, NOW.toEpochMilli())
        purchase(user.playerId)
        response = response.replace("T01:00:00Z", "T01:00:00.123456789Z")
        sync.syncNext(provider, START, NOW)
        sync.syncNext(provider, START, NOW.plusSeconds(3601))
        assertEquals("OK", text("SELECT sync_status FROM store_financial_orders"))
        assertEquals(1, count("analytics_financial_events"))
    } }

    @Test fun exactUsdJpyVndRoundTripAndUnsupportedHistoryRemainUnknown() = runTest {
        for (currency in listOf("USD", "JPY", "VND")) database {
            val user = PostgresGuestIdentityRepository(db).resolveGuest("Buyer", null, NOW.toEpochMilli())
            purchase(user.playerId)
            response = GoogleOrdersFinancialSourceTest.orderJson(currency, "9007199254740993", "0")
            sync.syncNext(provider, START.plusSeconds(86400), NOW)
            assertEquals(0, count("analytics_financial_events"))
            assertEquals("UNAVAILABLE", text("SELECT status FROM analytics_source_coverage WHERE source='GOOGLE_PLAY' AND environment='LIVE'"))
            sync.syncNext(provider, START, NOW.plusSeconds(3601))
            assertEquals(if (currency == "USD") "900719925474099300" else "9007199254740993",
                text("SELECT amount_minor::text FROM analytics_financial_events"))
            assertEquals(currency, text("SELECT currency FROM analytics_financial_events"))
        }
    }

    @Test fun failedLedgerCommitRollsBackEveryFactAndRemainsDueForRetry() = runTest { database {
        val user = PostgresGuestIdentityRepository(db).resolveGuest("Buyer", null, NOW.toEpochMilli())
        purchase(user.playerId)
        response = GoogleOrdersFinancialSourceTest.orderJson("USD", "10", "0", "PARTIALLY_REFUNDED", GoogleOrdersFinancialSourceTest.partial("2", "2026-10-02T01:00:00Z"))
        sql("CREATE FUNCTION reject_refund() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN IF NEW.event_type='REFUND' THEN RAISE EXCEPTION 'refund rejected'; END IF; RETURN NEW; END ${'$'}${'$'}")
        sql("CREATE CONSTRAINT TRIGGER reject_refund AFTER INSERT ON analytics_financial_events DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reject_refund()")
        assertFailsWith<SQLException> { sync.syncNext(provider, START, NOW) }
        assertEquals(0, count("analytics_financial_events"))
        assertEquals("PENDING", text("SELECT sync_status FROM store_financial_orders"))
        sql("DROP TRIGGER reject_refund ON analytics_financial_events")
        assertTrue(sync.syncNext(provider, START, NOW))
        assertEquals(2, count("analytics_financial_events"))
        assertEquals(1, count("wallet_transactions"))
    } }

    @Test fun deletedAccountRetryCannotRestoreLinkOrGrantAndConcurrentDeletionDoesNotBlockSource() = runTest { database {
        val account = PostgresAnalyticsFactsTest.account()
        assertTrue(PostgresAuthRepository(db).createAccount(account))
        purchase(account.userId.toString())
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        orderRequest = { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        coroutineScope {
            val pending = async(Dispatchers.IO) { sync.syncNext(provider, START, NOW) }
            withContext(Dispatchers.IO) { assertTrue(entered.await(10, TimeUnit.SECONDS)) }
            try { assertTrue(PostgresAuthRepository(db).deleteAccount(account.userId)) }
            finally { release.countDown() }
            assertTrue(pending.await())
        }
        assertEquals(StorePurchaseStatus.FAILED, purchase(account.userId.toString()).status)
        assertEquals(0, count("analytics_subject_links")); assertEquals(0, count("store_purchases"))
        assertEquals(1, count("analytics_financial_events")); assertEquals(1, count("store_financial_orders"))
    } }

    @Test fun sandboxBindingNeverPromotesTestPurchaseIntoLiveLedger() = runTest { database {
        sandbox = true
        val user = PostgresGuestIdentityRepository(db).resolveGuest("Buyer", null, NOW.toEpochMilli())
        assertEquals(StorePurchaseStatus.INVALID, purchase(user.playerId).status)
        assertEquals(0, count("store_financial_orders"))
        val sandboxService = StorePurchaseService(PostgresPlayerProfileRepository(db), GooglePlayPurchaseVerifier("example.app", credentials, sandboxAllowed = true, endpoint = endpoint))
        assertEquals(StorePurchaseStatus.GRANTED, sandboxService.verify(user.playerId, StorePurchaseRequest(requestId="test", store=StorePlatform.GOOGLE_PLAY,
            productId="fasttowin_gems_80", purchaseToken="private-token")).status)
        assertTrue(sync.syncNext(provider, START, NOW))
        assertEquals("SANDBOX", text("SELECT environment FROM analytics_financial_events"))
        assertEquals("UNAVAILABLE", text("SELECT status FROM analytics_source_coverage WHERE source='GOOGLE_PLAY' AND environment='LIVE'"))
    } }

    private suspend fun database(block: suspend Fixture.() -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL"); assumeTrue(!url.isNullOrBlank(), "Configured PostgreSQL required")
        val schema = "financial_test_${UUID.randomUUID().toString().replace("-", "")}"
        fun config() = HikariConfig().apply { jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize = 5 }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try { HikariDataSource(config().apply { this.schema = schema }).use { source ->
                Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                Fixture(source).use { it.block() }
            } } finally { setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }

    private class Fixture(val db: HikariDataSource) : AutoCloseable {
        var response = GoogleOrdersFinancialSourceTest.payload
        var code = 200
        var sandbox = false
        var orderRequest: () -> Unit = {}
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val purchase = exchange.requestURI.path.contains("/purchases/productsv2/")
                if (!purchase) orderRequest()
                val json = if (purchase) """{"orderId":"GPA.1234","purchaseStateContext":{"purchaseState":"PURCHASED"},"productLineItem":[{"productId":"fasttowin_gems_80"}]${if (sandbox) ",\"testPurchaseContext\":{}" else ""}}""" else response
                val bytes = json.toByteArray()
                exchange.sendResponseHeaders(if (exchange.requestHeaders.getFirst("Authorization") != "Bearer local-access") 403 else if (purchase) 200 else code, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val endpoint = URI.create("http://127.0.0.1:${server.address.port}/")
        val credentials = GoogleCredentials.create(AccessToken("local-access", Date(Long.MAX_VALUE)))
        val provider = GoogleOrdersFinancialSource("example.app", credentials, endpoint = endpoint)
        val sync = PostgresFinancialSyncRepository(db)
        val service = StorePurchaseService(PostgresPlayerProfileRepository(db), GooglePlayPurchaseVerifier("example.app", credentials, endpoint = endpoint))
        suspend fun purchase(userId: String) = service.verify(userId, StorePurchaseRequest(requestId = "request", store = StorePlatform.GOOGLE_PLAY, productId = "fasttowin_gems_80", purchaseToken = "private-token"))
        fun text(sql: String): String = db.connection.use { it.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); r.getString(1) } } }
        fun sql(sql: String) = db.connection.use { it.createStatement().use { s -> s.execute(sql) } }
        fun count(table: String): Long {
            if (text("SELECT to_regclass('$table') IS NOT NULL") == "f") return 0
            return text("SELECT count(*) FROM $table").toLong()
        }
        override fun close() { server.stop(0) }
    }
    companion object {
        val NOW = Instant.parse("2026-10-06T00:00:00Z")
        val START = Instant.parse("2026-10-01T00:00:00Z")
    }
}
