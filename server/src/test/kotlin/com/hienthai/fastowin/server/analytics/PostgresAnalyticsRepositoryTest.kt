package com.hienthai.fastowin.server.analytics

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.hashToken
import com.hienthai.fastowin.server.gameModule
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import com.zaxxer.hikari.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.time.Instant
import java.sql.Connection
import java.util.UUID
import kotlin.test.*

class PostgresAnalyticsRepositoryTest {
    @Test fun exactSeparateCurrenciesWholeRangeDistinctAndStableTopTen() = runTest { database {
        coverage("ITEM_PURCHASES")
        val buyer = UUID.randomUUID()
        item(buyer, "hat", "GOLD", "9007199254740993", "2026-10-01T17:00:00Z")
        item(buyer, "hat", "GOLD", "10", "2026-10-02T17:00:00Z")
        item(buyer, "gem", "GEMS", "7", "2026-10-01T17:00:00Z")
        val response = read("ITEM_SPEND,ITEM_PURCHASES,ITEM_BUYERS,TOP_ITEMS")
        val gold = response.metrics[0].groups.single { it.currency == "GOLD" }
        assertEquals("9007199254741003", gold.summary)
        assertEquals(listOf("9007199254740993", "10", "0"), gold.series.map { it.value })
        assertEquals("7", response.metrics[0].groups.single { it.currency == "GEMS" }.summary)
        assertEquals("1", response.metrics[2].groups.single { it.currency == "GOLD" }.summary)
        assertEquals(listOf("1", "1", "0"), response.metrics[2].groups.single { it.currency == "GOLD" }.series.map { it.value })
        assertEquals("hat", response.metrics[3].groups.single { it.currency == "GOLD" }.top.single().item)
        assertTrue(Json.encodeToString(response).contains("\"summary\":\"9007199254741003\""))
        assertEquals("10", read("ITEM_SPEND", extra = mapOf("item" to listOf("hat")), from = "2026-10-02T17:00:00Z").metrics.single().groups.single { it.currency == "GOLD" }.summary)
        repeat(12) { item(UUID.randomUUID(), "tie_${it.toString().padStart(2,'0')}", "GEMS", "1", "2026-10-01T17:00:00Z") }
        val top = read("TOP_ITEMS", extra = mapOf("currency" to listOf("GEMS"))).metrics.single().groups.single().top
        assertEquals(10, top.size)
        assertEquals(listOf("gem") + (0..8).map { "tie_${it.toString().padStart(2,'0')}" }, top.map { it.item })
    } }
    @Test fun registrationsUseBangkokBucketsAndHalfOpenBounds() = runTest { database {
        coverage("REGISTRATIONS")
        for (time in listOf("2026-09-30T16:59:59Z", "2026-09-30T17:00:00Z", "2026-10-04T16:59:59Z", "2026-10-04T17:00:00Z", "2026-10-05T17:00:00Z"))
            sql("INSERT INTO analytics_registrations(subject_key,registered_at) VALUES ('${UUID.randomUUID()}','$time')")
        val result = read("REGISTRATIONS", "2026-09-30T16:59:59Z", "2026-10-05T17:00:00Z", "WEEK").metrics.single().groups.single()
        assertEquals("4", result.summary)
        assertEquals(listOf("3", "1"), result.series.map { it.value })
        val month = read("REGISTRATIONS", "2026-09-30T16:59:59Z", "2026-10-05T17:00:00Z", "MONTH").metrics.single().groups.single()
        assertEquals(listOf("1", "3"), month.series.map { it.value })
    } }
    @Test fun incompleteCoverageNeverInventsZeroOrPercentage() = runTest { database {
        coverage("ITEM_PURCHASES", start = "2026-10-02T17:00:00Z")
        val result = read("ITEM_SPEND").metrics.single()
        assertEquals(CoverageStatus.PARTIAL, result.coverage.single().status)
        val group = result.groups.first()
        assertNull(group.summary)
        assertEquals(listOf(null, "0", "0"), group.series.map { it.value })
        assertNull(group.comparison.value); assertNull(group.comparison.percentageChange)
        sql("DELETE FROM analytics_source_coverage WHERE metric='ITEM_PURCHASES'")
        val missing = read("ITEM_SPEND").metrics.single()
        assertEquals(CoverageStatus.UNAVAILABLE, missing.coverage.single().status)
        assertTrue(missing.groups.all { it.summary == null && it.series.all { point -> point.value == null } })
    } }
    @Test fun unavailableProviderFixturesAreNotLiveRevenue() = runTest { database {
        financial("GOOGLE_PLAY", "LIVE", "PAYMENT", "9007199254740993", "THB", 2, "2026-10-02T01:00:00Z")
        val result = read("MONEY_PAID").metrics.single()
        assertTrue(result.coverage.all { it.status == CoverageStatus.UNAVAILABLE })
        assertTrue(result.groups.isEmpty())
    } }
    @Test fun boundedTrustedLedgerNetCanBeNegativeAndSourceScaleSandboxStaySeparate() = runTest { database {
        coverage("FINANCIAL", "GOOGLE_PLAY"); coverage("FINANCIAL", "APP_STORE")
        financial("GOOGLE_PLAY", "LIVE", "PAYMENT", "9007199254740993", "THB", 2, "2026-09-30T01:00:00Z")
        financial("GOOGLE_PLAY", "LIVE", "REFUND", "9007199254740993", "THB", 2, "2026-10-02T01:00:00Z")
        financial("GOOGLE_PLAY", "LIVE", "PAYMENT", "5", "USD", 2, "2026-10-02T01:00:00Z")
        financial("GOOGLE_PLAY", "LIVE", "PAYMENT", "8", "USD", 2, "2026-10-03T01:00:00Z")
        financial("APP_STORE", "LIVE", "PAYMENT", "3", "THB", 2, "2026-10-02T01:00:00Z")
        financial("GOOGLE_PLAY", "SANDBOX", "PAYMENT", "10000", "THB", 2, "2026-10-02T01:00:00Z")
        sql("UPDATE analytics_financial_events SET subject_key='${UUID.randomUUID()}' WHERE source='GOOGLE_PLAY'")
        val results = read("MONEY_NET,MONEY_PAID,MONEY_REFUNDED,STORE_PURCHASES,STORE_BUYERS").metrics
        val net = results[0].groups.single { it.source == "GOOGLE_PLAY" && it.currency == "THB" }
        assertEquals("-9007199254740993", net.summary); assertEquals(2, net.currencyExponent)
        assertEquals("-18014398509481986", net.comparison.absoluteChange)
        assertEquals("-200", net.comparison.percentageChange)
        assertEquals("13", results[1].groups.single { it.source == "GOOGLE_PLAY" && it.currency == "USD" }.summary)
        assertEquals("2", results[3].groups.single { it.source == "GOOGLE_PLAY" }.summary)
        assertEquals("1", results[4].groups.single { it.source == "GOOGLE_PLAY" }.summary)
        assertEquals(listOf("1", "1", "0"), results[4].groups.single { it.source == "GOOGLE_PLAY" }.series.map { it.value })
        assertEquals("-9007199254740993", read("MONEY_NET", extra = mapOf("store" to listOf("GOOGLE_PLAY"), "currency" to listOf("THB"), "product" to listOf("product"))).metrics.single().groups.single().summary)
        assertEquals("0", results[1].groups.single { it.source == "GOOGLE_PLAY" && it.currency == "THB" }.summary)
        assertNull(results[1].groups.single { it.source == "APP_STORE" }.comparison.percentageChange)
    } }
    @Test fun permissionsCheckEveryMetricBeforeAnyFactsQuery() = runTest { database {
        val operator = principal(AdminRole.OPERATOR); val support = principal(AdminRole.SUPPORT)
        sql("DROP TABLE analytics_item_purchases")
        assertEquals(403, assertFailsWith<AdminException> { read("ITEM_SPEND,MONEY_NET", token = operator) }.status)
        assertEquals(403, assertFailsWith<AdminException> { read("REGISTRATIONS", token = support) }.status)
        assertEquals("0", read("REGISTRATIONS", token = operator).metrics.single().groups.single().summary)
        assertEquals(401, assertFailsWith<AdminException> { read("REGISTRATIONS", token = null) }.status)
    } }
    @Test fun completeFinancialUnitsIncludeZeroRefundsAndNeverCombineDifferentExponents() = runTest { database {
        coverage("FINANCIAL", "GOOGLE_PLAY")
        financial("GOOGLE_PLAY", "LIVE", "PAYMENT", "100", "USD", 2, "2026-10-02T01:00:00Z")
        financial("GOOGLE_PLAY", "LIVE", "PAYMENT", "1000", "USD", 3, "2026-10-02T01:00:00Z")
        val response = read("MONEY_PAID,MONEY_REFUNDED,MONEY_NET", extra = mapOf("store" to listOf("GOOGLE_PLAY")))
        assertEquals(listOf("100", "1000"), response.metrics[0].groups.map { it.summary })
        assertEquals(listOf(2, 3), response.metrics[0].groups.map { it.currencyExponent })
        assertEquals(listOf("0", "0"), response.metrics[1].groups.map { it.summary })
        assertEquals(listOf("100", "1000"), response.metrics[2].groups.map { it.summary })
    } }
    @Test fun auditAndStorageFailureExposeNoResultAndReleaseSessionLock() = runTest { database {
        sql("CREATE FUNCTION reject_analytics_audit() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN IF NEW.action='ANALYTICS_READ' THEN RAISE EXCEPTION 'private storage failure'; END IF; RETURN NEW; END ${'$'}${'$'}")
        sql("CREATE TRIGGER reject_analytics_audit BEFORE INSERT ON admin_audit FOR EACH ROW EXECUTE FUNCTION reject_analytics_audit()")
        val error = assertFailsWith<AdminException> { read("REGISTRATIONS") }
        assertEquals(503, error.status); assertFalse(error.message.contains("private"))
        sql("DROP TRIGGER reject_analytics_audit ON admin_audit")
        assertEquals("0", read("REGISTRATIONS").metrics.single().groups.single().summary)
        assertEquals(1, number("SELECT count(*) FROM admin_audit WHERE action='ANALYTICS_READ'"))
        assertEquals(0, number("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND classid=1727 AND objid=3683667157"))
        sql("DROP TABLE analytics_registrations")
        assertEquals(503, assertFailsWith<AdminException> { read("REGISTRATIONS") }.status)
        assertEquals(1, number("SELECT count(*) FROM admin_audit WHERE action='ANALYTICS_READ'"))
    } }
    @Test fun revocationCommittedWhileReadWaitsIsObservedAfterLockAcquisition() = runTest { database {
        source.connection.use { blocker ->
            blocker.autoCommit = false
            blocker.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187349)") }
            coroutineScope {
                val pending = async(Dispatchers.IO) { runCatching { read("REGISTRATIONS") } }
                awaitWait("SELECT pg_advisory_lock(7421092187349)")
                blocker.createStatement().use { it.execute("UPDATE admin_accounts SET active=FALSE WHERE id='$ownerId'") }
                blocker.commit()
                val result = pending.await()
                assertTrue(result.isFailure, "A read waiting behind committed revocation must not return analytics")
                assertEquals(401, (result.exceptionOrNull() as AdminException).status)
            }
        }
        assertEquals(0, number("SELECT count(*) FROM admin_audit WHERE action='ANALYTICS_READ'"))
    } }
    @Test fun roleChangedToSupportWhileWaitingCannotReadAgainstEarlierSnapshot() = runTest { database {
        source.connection.use { blocker ->
            blocker.autoCommit = false
            blocker.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187349)") }
            coroutineScope {
                val pending = async(Dispatchers.IO) { runCatching { read("REGISTRATIONS") } }
                awaitWait("SELECT pg_advisory_lock(7421092187349)")
                blocker.createStatement().use { it.execute("UPDATE admin_accounts SET role='SUPPORT' WHERE id='$ownerId'") }
                blocker.commit()
                val result = pending.await()
                assertTrue(result.isFailure, "A read waiting behind a Support role change must not return analytics")
                assertEquals(403, (result.exceptionOrNull() as AdminException).status)
            }
        }
    } }
    @Test fun enabledHttpApiEnforcesRolesHeadersOriginAndSafeStorageFailures() = runTest { database {
        val operator = principal(AdminRole.OPERATOR); val support = principal(AdminRole.SUPPORT)
        testApplication {
            application { gameModule(adminService = admin, analyticsService = service) }
            suspend fun request(metric: String, token: String?, origin: String? = null) = client.get("/admin/api/analytics?metric=$metric&from=${time("2026-10-01T17:00:00Z")}&until=${time("2026-10-04T17:00:00Z")}") {
                token?.let { header(HttpHeaders.Cookie, "$ADMIN_COOKIE=$it") }
                origin?.let { header(HttpHeaders.Origin,it) }
            }
            for ((metric, token, status) in listOf(Triple("REGISTRATIONS", ownerToken, HttpStatusCode.OK),
                Triple("REGISTRATIONS", operator, HttpStatusCode.OK), Triple("MONEY_NET", operator, HttpStatusCode.Forbidden),
                Triple("REGISTRATIONS", support, HttpStatusCode.Forbidden), Triple("REGISTRATIONS", null, HttpStatusCode.Unauthorized),
                Triple("NOT_A_METRIC", ownerToken, HttpStatusCode.BadRequest))) {
                val response = request(metric, token)
                assertEquals(status, response.status); assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
                assertEquals("nosniff", response.headers["X-Content-Type-Options"])
                assertFalse(response.bodyAsText().contains(ownerToken))
            }
            assertEquals(HttpStatusCode.Forbidden, request("REGISTRATIONS", ownerToken, "https://other.example.com").status)
            sql("DROP TABLE analytics_registrations")
            val failure = request("REGISTRATIONS", ownerToken)
            assertEquals(HttpStatusCode.ServiceUnavailable, failure.status)
            assertFalse(failure.bodyAsText().contains("analytics_registrations"))
        }
    } }
    @Test fun factMutationDuringAuditCannotSplitSummarySeriesTopCoverageOrComparison() = runTest { database {
        coverage("ITEM_PURCHASES")
        val buyer = UUID.randomUUID(); item(buyer, "hat", "GOLD", "10", "2026-10-02T01:00:00Z")
        sql("CREATE FUNCTION wait_analytics_audit() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN IF NEW.action='ANALYTICS_READ' THEN PERFORM pg_advisory_xact_lock(918273645); END IF; RETURN NEW; END ${'$'}${'$'}")
        sql("CREATE TRIGGER wait_analytics_audit BEFORE INSERT ON admin_audit FOR EACH ROW EXECUTE FUNCTION wait_analytics_audit()")
        source.connection.use { blocker ->
            blocker.autoCommit = false; blocker.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(918273645)") }
            coroutineScope {
                val pending = async(Dispatchers.IO) { read("ITEM_SPEND,TOP_ITEMS") }
                awaitWait("INSERT INTO admin_audit")
                item(buyer, "new_item", "GOLD", "20", "2026-10-02T01:00:00Z")
                item(buyer, "prior", "GOLD", "30", "2026-09-29T01:00:00Z")
                sql("UPDATE analytics_source_coverage SET status='PARTIAL',freshness='2026-10-05T00:00:00Z' WHERE metric='ITEM_PURCHASES'")
                blocker.commit()
                val old = pending.await()
                val group = old.metrics[0].groups.first()
                assertEquals("10", group.summary); assertEquals("10", group.series.first().value)
                assertEquals("0", group.comparison.value); assertEquals(CoverageStatus.COMPLETE, old.metrics[0].coverage.single().status)
                assertEquals(time("2026-10-05T17:00:00Z"), old.metrics[0].coverage.single().freshness)
                assertEquals(listOf("hat"), old.metrics[1].groups.first().top.map { it.item })
            }
        }
        val next = read("ITEM_SPEND").metrics.single()
        assertEquals("30", next.groups.first().summary); assertEquals("30", next.groups.first().comparison.value)
        assertEquals(CoverageStatus.PARTIAL, next.coverage.single().status)
    } }

    private suspend fun database(block: suspend Fixture.() -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL"); assumeTrue(!url.isNullOrBlank(), "Configured local PostgreSQL required")
        val schema = "analytics_api_${UUID.randomUUID().toString().replace("-", "")}"
        fun config() = HikariConfig().apply { jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize = 5 }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try { HikariDataSource(config().apply { this.schema = schema }).use { source ->
                Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                Fixture(source).apply { initialize() }.block()
            } } finally { setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }
    private class Fixture(val source: HikariDataSource) {
        val admin = AdminService(PostgresAdminRepository(source), AdminConfiguration("http://localhost:18082", false), nowMillis = { NOW })
        val service = AnalyticsService(PostgresAnalyticsRepository(source), admin) { NOW }
        lateinit var ownerToken: String; lateinit var ownerId: String
        suspend fun initialize() { ownerToken = principal(AdminRole.OWNER); coverage("REGISTRATIONS") }
        suspend fun principal(role: AdminRole): String {
            val id = UUID.randomUUID().toString(); val token = UUID.randomUUID().toString().replace("-", "") + "abcdefghijk"
            admin.repository.transaction {
                putAccount(AdminAccount(AdminIdentity(id, "$id@example.com", role), "test-hash"))
                putSession(AdminSession(hashToken(token), id, "test-csrf", NOW + 3600000))
            }
            if (role == AdminRole.OWNER) ownerId = id
            return token
        }
        suspend fun read(metric: String, from: String = "2026-10-01T17:00:00Z", until: String = "2026-10-04T17:00:00Z", bucket: String = "DAY",
            extra: Map<String,List<String>> = emptyMap(), token: String? = ownerToken) = service.read(token, null,
            mapOf("metric" to listOf(metric), "from" to listOf("${time(from)}"), "until" to listOf("${time(until)}"), "bucket" to listOf(bucket)) + extra)
        fun coverage(metric: String, source: String = "SERVER", start: String = "2026-09-01T00:00:00Z") = sql("UPDATE analytics_source_coverage SET status='COMPLETE',coverage_start='$start',freshness='2026-10-05T17:00:00Z' WHERE metric='$metric' AND source='$source'")
        fun item(buyer: UUID, item: String, currency: String, amount: String, time: String) = sql("INSERT INTO analytics_item_purchases(event_id,subject_key,item_id,currency,amount,purchased_at) VALUES ('${UUID.randomUUID()}','$buyer','$item','$currency',$amount,'$time')")
        fun financial(source: String, environment: String, type: String, amount: String, currency: String, exponent: Int, time: String) = sql("INSERT INTO analytics_financial_events(source,environment,event_id,event_type,transaction_id,product_id,subject_key,amount_minor,currency,currency_exponent,occurred_at) VALUES ('$source','$environment','${UUID.randomUUID()}','$type','${UUID.randomUUID()}','product','${UUID.randomUUID()}',$amount,'$currency',$exponent,'$time')")
        fun sql(query: String) = source.connection.use { it.createStatement().use { s -> s.execute(query) } }
        fun number(query: String) = source.connection.use { it.createStatement().use { s -> s.executeQuery(query).use { r -> r.next(); r.getLong(1) } } }
        suspend fun awaitWait(query: String) = withTimeout(10000) {
            while (number("SELECT count(*) FROM pg_stat_activity WHERE wait_event='advisory' AND query LIKE '$query%'") == 0L) delay(10)
        }
    }
    companion object { fun time(value: String) = Instant.parse(value).toEpochMilli(); val NOW = time("2026-10-06T17:00:00Z") }
}
