package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.sql.SQLException

class StorePurchaseRoutesTest {
    private val body = """{"requestId":"receipt-1","store":"GOOGLE_PLAY","productId":"fasttowin_gems_80","purchaseToken":"paid-receipt"}"""
    @Test fun `auth database failure returns safe 503 rather than unauthorized or a grant`() = testApplication {
        val repository = object : AuthRepository by InMemoryAuthRepository() {
            override suspend fun findActiveSession(accessTokenHash: String, nowMillis: Long): AuthenticatedAccount? = throw SQLException("private SQL receipt")
        }
        val auth = AuthenticationService(repository, passwordHasher = PasswordHasher(1000))
        val session = runBlocking { assertIs<AuthResult.Success>(auth.register("auth-failure@example.com", "GoodPassword123!", "Player", "test")).session }
        application { gameModule(authService = auth) }
        val response = client.post("/api/store-purchases/verify") { bearerAuth(session.accessToken); contentType(ContentType.Application.Json); setBody(this@StorePurchaseRoutesTest.body) }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertFalse(response.bodyAsText().contains("private SQL"))
    }
    @Test fun `storage SQL failure returns safe 503 during closure`() = testApplication {
        val auth = AuthenticationService(InMemoryAuthRepository(), passwordHasher = PasswordHasher(1000))
        val session = runBlocking { assertIs<AuthResult.Success>(auth.register("storage-failure@example.com", "GoodPassword123!", "Player", "test")).session }
        val repository = object : PlayerProfileRepository by NoOpPlayerProfileRepository {
            override suspend fun grantStorePurchase(playerId: String, store: String, productId: String, transactionId: String, gems: Int): StorePurchaseGrantStatus = throw SQLException("private SQL")
        }
        val engine = GameEngine(playerProfileRepository = repository, storePurchaseVerifier = StorePurchaseVerifier { StoreVerificationResult(StoreVerificationStatus.PURCHASED, "") })
        application { gameModule(engine, auth, serviceStatusProvider = { ServiceStatusResponse(true) }) }
        val response = client.post("/api/store-purchases/verify") { bearerAuth(session.accessToken); contentType(ContentType.Application.Json); setBody(this@StorePurchaseRoutesTest.body) }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertFalse(response.bodyAsText().contains("private SQL"))
    }
    @Test fun `strict bounded JSON and player rate limits never call provider for rejected request`() = testApplication {
        val auth = AuthenticationService(InMemoryAuthRepository(), passwordHasher = PasswordHasher(1000))
        val session = runBlocking { assertIs<AuthResult.Success>(auth.register("limits@example.com", "GoodPassword123!", "Player", "test")).session }
        var providerCalls = 0
        val engine = GameEngine(storePurchaseVerifier = StorePurchaseVerifier { providerCalls++; StoreVerificationResult(StoreVerificationStatus.INVALID, "", "BillingIncomplete") })
        application { gameModule(engine, auth, rateLimitPolicies = ServerRateLimitPolicies(verifyPurchasePerPlayer = RateLimitPolicy(7, 60_000), verifyPurchasePerIp = RateLimitPolicy(100, 60_000))) }
        suspend fun send(payload: String) = client.post("/api/store-purchases/verify") { bearerAuth(session.accessToken); contentType(ContentType.Application.Json); setBody(payload) }
        assertEquals(HttpStatusCode.BadRequest, send("{").status)
        assertEquals(HttpStatusCode.BadRequest, send(body.dropLast(1) + ",\"userId\":\"victim\"}").status)
        assertEquals(HttpStatusCode.BadRequest, send("x".repeat(32769)).status)
        val invalid = send(body.replace("paid-receipt", "x".repeat(4097)))
        assertEquals(StorePurchaseStatus.INVALID, ProtocolJson.decodeFromString<ServerMessage.StorePurchaseResult>(invalid.bodyAsText()).status)
        repeat(3) { assertEquals(HttpStatusCode.OK, send(body).status) }
        assertEquals(HttpStatusCode.TooManyRequests, send(body).status)
        assertEquals(3, providerCalls)
    }
    @Test fun `service keeps current verifier mapping and propagates cancellation without grant`() = runTest {
        val request = StorePurchaseRequest(StorePlatform.GOOGLE_PLAY, "fasttowin_gems_80", "receipt", "request")
        for (status in listOf(StoreVerificationStatus.INVALID, StoreVerificationStatus.UNAVAILABLE)) {
            val service = StorePurchaseService(NoOpPlayerProfileRepository, StorePurchaseVerifier { StoreVerificationResult(status, "safe", "CustomKey", mapOf("a" to "b")) })
            val response = service.verify("player", request)
            assertEquals(if (status == StoreVerificationStatus.INVALID) StorePurchaseStatus.INVALID else StorePurchaseStatus.UNAVAILABLE, response.status)
            assertEquals("CustomKey", response.messageKey)
            assertEquals(mapOf("a" to "b"), response.messageArgs)
        }
        val service = StorePurchaseService(NoOpPlayerProfileRepository, StorePurchaseVerifier { throw CancellationException("cancel") })
        assertFailsWith<CancellationException> { service.verify("player", request) }
    }
    @Test fun `paid receipt finalizes with no socket during maintenance and replays once`() = testApplication {
        val auth = AuthenticationService(InMemoryAuthRepository(), passwordHasher = PasswordHasher(1000))
        val session = runBlocking { assertIs<AuthResult.Success>(auth.register("receipt@example.com", "GoodPassword123!", "Player", "test")).session }
        val receipts = mutableSetOf<String>()
        var grants = 0
        val repository = object : PlayerProfileRepository by NoOpPlayerProfileRepository {
            override suspend fun grantStorePurchase(playerId: String, store: String, productId: String, transactionId: String, gems: Int): StorePurchaseGrantStatus {
                assertEquals(session.userId, playerId)
                return if (receipts.add(transactionId)) { grants++; StorePurchaseGrantStatus.GRANTED } else StorePurchaseGrantStatus.ALREADY_GRANTED
            }
        }
        val engine = GameEngine(playerProfileRepository = repository, storePurchaseVerifier = StorePurchaseVerifier {
            StoreVerificationResult(StoreVerificationStatus.PURCHASED, "verified")
        })
        application { gameModule(engine = engine, authService = auth, serviceStatusProvider = { ServiceStatusResponse(true) }) }
        suspend fun verify(token: String? = session.accessToken, body: String = """{"requestId":"receipt-1","store":"GOOGLE_PLAY","productId":"fasttowin_gems_80","purchaseToken":"paid-receipt"}""") = client.post("/api/store-purchases/verify") {
            token?.let { bearerAuth(it) }; contentType(ContentType.Application.Json); setBody(body)
        }
        val denied = verify(null)
        assertEquals(HttpStatusCode.Unauthorized, denied.status)
        assertEquals("no-store", denied.headers[HttpHeaders.CacheControl])
        val first = verify()
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(StorePurchaseStatus.GRANTED, ProtocolJson.decodeFromString<ServerMessage.StorePurchaseResult>(first.bodyAsText()).status)
        val replay = verify()
        assertEquals(StorePurchaseStatus.ALREADY_GRANTED, ProtocolJson.decodeFromString<ServerMessage.StorePurchaseResult>(replay.bodyAsText()).status)
        assertEquals(1, grants)
        assertFalse(first.bodyAsText().contains("paid-receipt"))
    }
}
