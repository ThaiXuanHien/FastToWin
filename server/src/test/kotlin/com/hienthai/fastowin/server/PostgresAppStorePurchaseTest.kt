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

class PostgresAppStorePurchaseTest {
    @Test fun `re-signed App Store transaction grants exactly once and cannot move between accounts`() = runBlocking {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL absent")
        val schema = "appstore_receipt_test_${UUID.randomUUID().toString().replace("-", "")}"
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize = 3
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { sql -> sql.execute("CREATE SCHEMA $schema") } }
            try {
                HikariDataSource(config().apply { this.schema = schema; jdbcUrl = "$url${if (url!!.contains('?')) '&' else '?'}currentSchema=$schema" }).use { source ->
                    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                    val auth = AuthenticationService(PostgresAuthRepository(source), passwordHasher = PasswordHasher(1000))
                    val owner = assertIs<AuthResult.Success>(auth.register("ios-owner@example.invalid", "GoodPassword123!", "Owner", "test")).session.userId
                    val other = assertIs<AuthResult.Success>(auth.register("ios-other@example.invalid", "GoodPassword123!", "Other", "test")).session.userId
                    val profiles = PostgresPlayerProfileRepository(source)
                    val chain = AppStoreTestChain()
                    val service = StorePurchaseService(profiles, EnvironmentStorePurchaseVerifier("prod",
                        appStoreVerifier = AppStorePurchaseVerifier(trustAnchors = setOf(chain.root))))
                    fun request(token: String, id: String) = StorePurchaseRequest(StorePlatform.APP_STORE, "fasttowin_gems_250", token, id)
                    val product = mapOf("productId" to "fasttowin_gems_250")

                    val granted = service.verify(owner, request(chain.transaction(owner, product), "ios-1"))
                    assertEquals(StorePurchaseStatus.GRANTED, granted.status)
                    assertEquals(250, granted.gemsGranted)
                    // Transaction.updates / currentEntitlements re-deliver a freshly signed JWS for the same purchase.
                    val resigned = chain.transaction(owner, product + mapOf("signedDate" to Instant.now().minusSeconds(5).toEpochMilli()))
                    assertEquals(StorePurchaseStatus.ALREADY_GRANTED, service.verify(owner, request(resigned, "ios-2")).status)
                    assertEquals(StorePurchaseStatus.INVALID, service.verify(other, request(resigned, "ios-3")).status)
                    val sandbox = chain.transaction(owner, product + mapOf("environment" to "Sandbox"))
                    assertEquals(StorePurchaseStatus.GRANTED, service.verify(owner, request(sandbox, "ios-4")).status)

                    fun count(sql: String) = source.connection.use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> r.next(); r.getInt(1) } } }
                    assertEquals(2, count("SELECT count(*) FROM store_purchases WHERE store = 'APP_STORE'"))
                    assertEquals(2, count("SELECT count(*) FROM wallet_transactions WHERE source_type = 'STORE_PURCHASE'"))
                    assertEquals(0, count("SELECT count(*) FROM store_financial_orders"))
                    assertEquals(500, profiles.findByPlayerId(owner)!!.progression.gems)
                    assertEquals(0, profiles.findByPlayerId(other)!!.progression.gems)
                }
            } finally {
                setup.connection.use { it.createStatement().use { sql -> sql.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }
}
