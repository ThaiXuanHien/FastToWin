package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.zaxxer.hikari.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.*

class PostgresStorePurchaseRoutesTest {
    @Test fun `HTTP and legacy socket race uses one durable receipt and wallet grant during closure`() = runBlocking {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL absent")
        val schema = "store_receipt_test_${UUID.randomUUID().toString().replace("-", "") }"
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize = 5
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { sql -> sql.execute("CREATE SCHEMA $schema") } }
            try {
                HikariDataSource(config().apply { this.schema = schema; jdbcUrl = "$url${if (url!!.contains('?')) '&' else '?'}currentSchema=$schema" }).use { source ->
                    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                    val auth = AuthenticationService(PostgresAuthRepository(source), passwordHasher = PasswordHasher(1000))
                    val owner = assertIs<AuthResult.Success>(auth.register("owner@example.invalid", "GoodPassword123!", "Owner", "test")).session
                    val other = assertIs<AuthResult.Success>(auth.register("other@example.invalid", "GoodPassword123!", "Other", "test")).session
                    val profiles = PostgresPlayerProfileRepository(source)
                    val service = StorePurchaseService(profiles, StorePurchaseVerifier { StoreVerificationResult(StoreVerificationStatus.PURCHASED, "verified") })
                    val engine = GameEngine(playerProfileRepository = profiles, storePurchaseService = service)
                    engine.connectAccount(AuthenticatedAccount(UUID.fromString(owner.userId), "Owner"))
                    // Stop application timers before the repository pool is closed.
                    testApplication {
                        application { gameModule(engine, auth, serviceStatusProvider = { ServiceStatusResponse(true) }) }
                        val receipt = StorePurchaseRequest(StorePlatform.GOOGLE_PLAY, "fasttowin_gems_80", "race-receipt", "race-1")
                        suspend fun http(session: AuthSessionResponse = owner): io.ktor.client.statement.HttpResponse = client.post("/api/store-purchases/verify") {
                            bearerAuth(session.accessToken); contentType(ContentType.Application.Json)
                            setBody(ProtocolJson.encodeToString(StorePurchaseRequest.serializer(), receipt))
                        }
                        val results = coroutineScope {
                            val legacy = async { engine.handle(owner.userId, ClientMessage.VerifyStorePurchase(receipt.requestId, receipt.store, receipt.productId, receipt.purchaseToken)).map { it.message }.filterIsInstance<ServerMessage.StorePurchaseResult>().single() }
                            val response = async { val r = http(); assertEquals(HttpStatusCode.OK, r.status); ProtocolJson.decodeFromString<ServerMessage.StorePurchaseResult>(r.bodyAsText()) }
                            listOf(legacy.await(), response.await())
                        }
                        assertEquals(setOf(StorePurchaseStatus.GRANTED, StorePurchaseStatus.ALREADY_GRANTED), results.map { it.status }.toSet())
                        assertEquals(StorePurchaseStatus.ALREADY_GRANTED, ProtocolJson.decodeFromString<ServerMessage.StorePurchaseResult>(http().bodyAsText()).status)
                        assertEquals(StorePurchaseStatus.INVALID, ProtocolJson.decodeFromString<ServerMessage.StorePurchaseResult>(http(other).bodyAsText()).status)
                        fun count(table: String, where: String = "") = source.connection.use { it.createStatement().use { sql -> sql.executeQuery("SELECT count(*) FROM $table $where").use { rows -> rows.next(); rows.getInt(1) } } }
                        assertEquals(1, count("store_purchases"))
                        assertEquals(1, count("wallet_transactions", "WHERE source_type = 'STORE_PURCHASE'"))
                        assertEquals(80, profiles.findByPlayerId(owner.userId)!!.progression.gems)
                        assertEquals(0, profiles.findByPlayerId(other.userId)!!.progression.gems)
                        source.connection.use { it.createStatement().use { sql -> sql.execute("ALTER TABLE store_purchases RENAME TO store_purchases_unavailable") } }
                        val failure = http()
                        assertEquals(HttpStatusCode.ServiceUnavailable, failure.status)
                        assertFalse(failure.bodyAsText().contains(schema))
                        source.connection.use { it.createStatement().use { sql -> sql.execute("ALTER TABLE store_purchases_unavailable RENAME TO store_purchases") } }
                        assertEquals(1, count("store_purchases"))
                        assertEquals(1, count("wallet_transactions", "WHERE source_type = 'STORE_PURCHASE'"))
                    }
                }
            } finally {
                setup.connection.use { it.createStatement().use { sql -> sql.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }
}
