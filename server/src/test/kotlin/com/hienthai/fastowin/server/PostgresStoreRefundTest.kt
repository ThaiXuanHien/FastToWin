package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.StorePlatform
import com.hienthai.fastowin.protocol.StorePurchaseRequest
import com.hienthai.fastowin.protocol.StorePurchaseStatus
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class PostgresStoreRefundTest {
    @Test fun `App Store purchase records revenue and a refund takes back Gems once without going negative`() = runBlocking {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL absent")
        val schema = "store_refund_test_${UUID.randomUUID().toString().replace("-", "")}"
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize = 3
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { sql -> sql.execute("CREATE SCHEMA $schema") } }
            try {
                HikariDataSource(config().apply { this.schema = schema; jdbcUrl = "$url${if (url!!.contains('?')) '&' else '?'}currentSchema=$schema" }).use { source ->
                    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                    val auth = AuthenticationService(PostgresAuthRepository(source), passwordHasher = PasswordHasher(1000))
                    val player = assertIs<AuthResult.Success>(auth.register("refund@example.invalid", "GoodPassword123!", "Refund", "test")).session.userId
                    val profiles = PostgresPlayerProfileRepository(source)
                    val chain = AppStoreTestChain()
                    val purchases = StorePurchaseService(profiles, EnvironmentStorePurchaseVerifier("prod",
                        appStoreVerifier = AppStorePurchaseVerifier(trustAnchors = setOf(chain.root))))
                    val notifications = AppStoreNotificationService(PostgresStoreRefundRepository(source),
                        signed = AppleSignedPayloadVerifier(setOf(chain.root)))
                    fun count(sql: String) = source.connection.use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> r.next(); r.getLong(1) } } }
                    suspend fun gems() = profiles.findByPlayerId(player)!!.progression.gems

                    val purchase = mapOf("productId" to "fasttowin_gems_250", "transactionId" to "2000000555",
                        "price" to 49_000_000, "currency" to "VND")
                    val granted = purchases.verify(player, StorePurchaseRequest(StorePlatform.APP_STORE, "fasttowin_gems_250",
                        chain.transaction(player, purchase), "buy-1"))
                    assertEquals(StorePurchaseStatus.GRANTED, granted.status)
                    assertEquals(1, count("""SELECT count(*) FROM analytics_financial_events WHERE source='APP_STORE' AND environment='LIVE'
                        AND event_type='PAYMENT' AND transaction_id='2000000555' AND amount_minor=49000000 AND currency='VND' AND currency_exponent=3"""))
                    assertEquals(1, count("SELECT count(*) FROM analytics_source_coverage WHERE metric='FINANCIAL' AND source='APP_STORE' AND environment='LIVE' AND status='PARTIAL'"))
                    // A re-signed copy of the same purchase neither grants again nor duplicates revenue.
                    purchases.verify(player, StorePurchaseRequest(StorePlatform.APP_STORE, "fasttowin_gems_250",
                        chain.transaction(player, purchase + mapOf("signedDate" to Instant.now().minusSeconds(3).toEpochMilli())), "buy-2"))
                    assertEquals(1, count("SELECT count(*) FROM analytics_financial_events WHERE event_type='PAYMENT'"))

                    // The player spends most of the pack before Apple refunds it.
                    source.connection.use { c -> c.prepareStatement("UPDATE player_stats SET gems=100 WHERE user_id=?").use {
                        it.setObject(1, UUID.fromString(player)); it.executeUpdate() } }
                    val refunded = chain.transaction(player, purchase + mapOf("revocationDate" to Instant.now().toEpochMilli()))
                    val notice = chain.notification("REFUND", refunded)
                    assertEquals(AppStoreNotificationOutcome.APPLIED, notifications.handle(notice))
                    assertEquals(0, gems(), "takes back what is left, never below zero")
                    assertEquals(1, count("SELECT count(*) FROM wallet_transactions WHERE source_type='STORE_REFUND' AND gems_delta=-100"))
                    assertEquals(1, count("SELECT count(*) FROM store_purchases WHERE refunded_at IS NOT NULL AND gems_revoked=100"))
                    assertEquals(1, count("SELECT count(*) FROM analytics_financial_events WHERE event_type='REFUND' AND amount_minor=49000000"))

                    // Apple retries the same notification, then sends another one for the same purchase.
                    source.connection.use { c -> c.prepareStatement("UPDATE player_stats SET gems=40 WHERE user_id=?").use {
                        it.setObject(1, UUID.fromString(player)); it.executeUpdate() } }
                    assertEquals(AppStoreNotificationOutcome.APPLIED, notifications.handle(notice))
                    assertEquals(AppStoreNotificationOutcome.APPLIED, notifications.handle(chain.notification("REFUND", refunded)))
                    assertEquals(40, gems())
                    assertEquals(1, count("SELECT count(*) FROM wallet_transactions WHERE source_type='STORE_REFUND'"))
                    assertEquals(1, count("SELECT count(*) FROM analytics_financial_events WHERE event_type='REFUND'"))

                    // A refund for a purchase this server never granted changes nothing.
                    val unknown = chain.transaction(player, purchase + mapOf("transactionId" to "2000000777",
                        "revocationDate" to Instant.now().toEpochMilli()))
                    assertEquals(AppStoreNotificationOutcome.APPLIED, notifications.handle(chain.notification("REFUND", unknown)))
                    assertEquals(40, gems())
                }
            } finally {
                setup.connection.use { it.createStatement().use { sql -> sql.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }
}
