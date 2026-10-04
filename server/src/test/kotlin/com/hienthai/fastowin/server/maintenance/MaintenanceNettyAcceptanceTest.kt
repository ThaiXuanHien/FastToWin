package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.URI
import java.net.ServerSocket
import java.net.InetAddress
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlin.test.*

/** Real loopback Netty + isolated PostgreSQL. Provider verification is an explicit test double. */
class MaintenanceNettyAcceptanceTest {
    @Test fun `live override blocks opening while authenticated paid receipts finish exactly once over real HTTP`() = runBlocking<Unit> {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL required")
        require(url == "jdbc:postgresql://127.0.0.1:15432/admin_test") { "Only owned local test database allowed" }
        val schema = "maintenance_live_${UUID.randomUUID().toString().replace("-", "") }"
        fun configuration() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize = 5
        }
        HikariDataSource(configuration()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try {
                HikariDataSource(configuration().apply { this.schema = schema; jdbcUrl = "$url?currentSchema=$schema" }).use { source ->
                    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                    val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
                    val configuredOrigin = "http://127.0.0.1:$port"
                    val auth = AuthenticationService(PostgresAuthRepository(source), passwordHasher = PasswordHasher(1000))
                    val player = assertIs<AuthResult.Success>(auth.register("paid@example.invalid", "LocalPassword123!", "Paid", "test")).session
                    val other = assertIs<AuthResult.Success>(auth.register("other@example.invalid", "LocalPassword123!", "Other", "test")).session
                    val profiles = PostgresPlayerProfileRepository(source)
                    val receiptService = StorePurchaseService(profiles, StorePurchaseVerifier {
                        StoreVerificationResult(StoreVerificationStatus.PURCHASED, "Trusted fixture only")
                    })
                    lateinit var admins: AdminService
                    lateinit var service: MaintenanceService
                    val engine = GameEngine(playerProfileRepository = profiles, storePurchaseService = receiptService)
                    val server = embeddedServer(Netty, host = "127.0.0.1", port = port) {
                        admins = AdminService(PostgresAdminRepository(source), AdminConfiguration(configuredOrigin, false), PasswordHasher(1000))
                        service = MaintenanceService(PostgresMaintenanceRepository(source), admins, environmentOverride = true)
                        gameModule(engine = engine, authService = auth, adminService = admins, maintenanceService = service,
                            allowedWebOriginsValue = configuredOrigin)
                    }
                    server.start(wait = false)
                    val actualPort = server.engine.resolvedConnectors().single().port
                    val base = "http://127.0.0.1:$actualPort"
                    val origin = configuredOrigin
                    assertEquals(configuredOrigin, base)
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().let { client ->
                        try {
                            fun request(path: String, body: String? = null, headers: Map<String, String> = emptyMap()): HttpRequest {
                                val builder = HttpRequest.newBuilder(URI(base + path)).timeout(Duration.ofSeconds(10))
                                headers.forEach { (key, value) -> builder.header(key, value) }
                                if (body != null) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
                                return builder.build()
                            }
                            fun send(path: String, body: String? = null, headers: Map<String, String> = emptyMap()) =
                                client.send(request(path, body, headers), HttpResponse.BodyHandlers.ofString())
                            val public = send("/api/status")
                            assertEquals(200, public.statusCode()); assertEquals("no-store", public.headers().firstValue("Cache-Control").get())
                            assertEquals(MaintenanceState.MAINTENANCE, ProtocolJson.decodeFromString<ServiceStatusResponse>(public.body()).state)
                            assertEquals(401, send("/admin/api/maintenance").statusCode())
                            admins.bootstrapOwner("owner@example.invalid", "LocalPassword123!")
                            val owner = admins.login("owner@example.invalid", "LocalPassword123!", origin, "127.0.0.1")
                            val ownerHeaders = mapOf("Cookie" to "fasttowin_admin=${owner.token}", "X-Admin-CSRF" to owner.response.csrfToken, "Origin" to origin)
                            val observation = ProtocolJson.decodeFromString<MaintenanceAdminResponse>(send("/admin/api/maintenance", headers = ownerHeaders).body())
                            assertEquals(MaintenanceSource.ENVIRONMENT, observation.effective.source)
                            assertEquals(MaintenanceState.ACTIVE, observation.persisted.state)
                            val off = MaintenanceUpdateRequest(0, "cannot-open", "Local override acceptance", enabled = false)
                            val denied = send("/admin/api/maintenance", ProtocolJson.encodeToString(MaintenanceUpdateRequest.serializer(), off), ownerHeaders)
                            assertEquals(409, denied.statusCode())
                            val receipt = StorePurchaseRequest(StorePlatform.GOOGLE_PLAY, "fasttowin_gems_80", "trusted-local-paid-fixture", "netty-paid-1")
                            val body = ProtocolJson.encodeToString(StorePurchaseRequest.serializer(), receipt)
                            val playerHeaders = mapOf("Authorization" to "Bearer ${player.accessToken}")
                            val requests = List(2) { client.sendAsync(request("/api/store-purchases/verify", body, playerHeaders), HttpResponse.BodyHandlers.ofString()) }
                            val results = requests.map { future ->
                                val response = future.join(); assertEquals(200, response.statusCode())
                                ProtocolJson.decodeFromString<ServerMessage.StorePurchaseResult>(response.body()).status
                            }
                            assertEquals(setOf(StorePurchaseStatus.GRANTED, StorePurchaseStatus.ALREADY_GRANTED), results.toSet())
                            assertEquals(StorePurchaseStatus.ALREADY_GRANTED, ProtocolJson.decodeFromString<ServerMessage.StorePurchaseResult>(send("/api/store-purchases/verify", body, playerHeaders).body()).status)
                            assertEquals(StorePurchaseStatus.INVALID, ProtocolJson.decodeFromString<ServerMessage.StorePurchaseResult>(send("/api/store-purchases/verify", body, mapOf("Authorization" to "Bearer ${other.accessToken}")).body()).status)
                            assertEquals(401, send("/api/store-purchases/verify", body).statusCode())
                            fun count(table: String, where: String = "") = source.connection.use { c -> c.createStatement().use { s ->
                                s.executeQuery("SELECT count(*) FROM $table $where").use { rows -> rows.next(); rows.getInt(1) }
                            } }
                            assertEquals(1, count("store_purchases"))
                            assertEquals(1, count("wallet_transactions", "WHERE source_type='STORE_PURCHASE'"))
                            assertEquals(80, profiles.findByPlayerId(player.userId)!!.progression.gems)
                            assertEquals(0, profiles.findByPlayerId(other.userId)!!.progression.gems)
                            admins.logout(owner.token, owner.response.csrfToken, origin)
                            assertEquals(401, send("/admin/api/maintenance", headers = ownerHeaders).statusCode())
                            println("NETTY_ACCEPTANCE PASS override=ENVIRONMENT saved=ACTIVE disable=409 receipts=1 walletRows=1 gems=80 otherGems=0 replay=ALREADY_GRANTED unauth=401 revokedAdmin=401 provider=TRUSTED_TEST_DOUBLE")
                        } finally { server.stop(100, 3000) }
                    }
                }
            } finally {
                // Only the UUID schema created by this test is removed; existing databases/schemas are untouched.
                setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }
}
