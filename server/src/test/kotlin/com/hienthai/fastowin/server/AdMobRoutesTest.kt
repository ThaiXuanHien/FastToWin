package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.ads.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.sql.SQLException
import kotlin.test.*

class AdMobRoutesTest {
    @Test fun `session creation requires access token even when ads are disabled`() = testApplication {
        application { gameModule() }
        val response = client.post("/api/rewarded-ads/sessions") {
            contentType(ContentType.Application.Json); setBody("""{"requestId":"request-1"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }
    @Test fun `disabled callback and authenticated session creation fail closed`() = testApplication {
        val auth = AuthenticationService(InMemoryAuthRepository(), passwordHasher = PasswordHasher(1000))
        val session = runBlocking { assertIs<AuthResult.Success>(auth.register("admob-off@example.com", "GoodPassword123!", "Player", "test")).session }
        application { gameModule(authService = auth) }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/rewarded-ads/ssv?invalid=1").status)
        val off = client.post("/api/rewarded-ads/sessions") {
            bearerAuth(session.accessToken); contentType(ContentType.Application.Json); setBody("""{"requestId":"request"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, off.status)
        assertEquals("UNAVAILABLE", ProtocolJson.decodeFromString<AuthErrorResponse>(off.bodyAsText()).code)
    }
    @Test fun `raw signed HTTP query grants owner and other account cannot read session`() = testApplication {
        val f = AdMobFixture(); val auth = AuthenticationService(InMemoryAuthRepository(), passwordHasher = PasswordHasher(1000))
        val owner = runBlocking { assertIs<AuthResult.Success>(auth.register("admob-owner@example.com", "GoodPassword123!", "Owner", "test")).session }
        val other = runBlocking { assertIs<AuthResult.Success>(auth.register("admob-other@example.com", "GoodPassword123!", "Other", "test")).session }
        application { gameModule(authService = auth, adMobService = f.service) }
        suspend fun prepare(payload: String) = client.post("/api/rewarded-ads/sessions") {
            bearerAuth(owner.accessToken); contentType(ContentType.Application.Json); setBody(payload)
        }
        for (body in listOf("{", "x".repeat(1025), """{"requestId":"request","userId":"other"}""", """{"requestId":"invalid space"}""")) {
            assertEquals(HttpStatusCode.BadRequest, prepare(body).status)
        }
        val response = prepare("""{"requestId":"request-1"}""")
        assertEquals(HttpStatusCode.OK, response.status)
        val session = ProtocolJson.decodeFromString<RewardedAdSessionView>(response.bodyAsText())
        val encodedNonce = "%" + session.customData.first().code.toString(16).uppercase() + session.customData.drop(1)
        val cannedProbe = f.signed("console-probe", unit = "0", reward = "10", item = "coins", timestamp = 1)
        assertEquals(HttpStatusCode.OK, client.get("/api/rewarded-ads/ssv?$cannedProbe").status)
        val query = f.signed(encodedNonce)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/rewarded-ads/ssv?" + query.replace("reward_amount=2", "reward_amount=3")).status)
        assertEquals(HttpStatusCode.OK, client.get("/api/rewarded-ads/ssv?$query").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/rewarded-ads/ssv?$query").status)
        val granted = client.get("/api/rewarded-ads/sessions/${session.sessionId}") { bearerAuth(owner.accessToken) }
        assertEquals(HttpStatusCode.OK, granted.status)
        assertEquals("no-store", granted.headers[HttpHeaders.CacheControl])
        val grantedQuota = assertNotNull(ProtocolJson.decodeFromString<RewardedAdSessionView>(granted.bodyAsText()).quota)
        assertEquals(12, grantedQuota.remainingMatches)
        assertEquals(RewardedAdAvailability.DEV_SIMULATED, grantedQuota.rewardedAdAvailability)
        val hidden = client.get("/api/rewarded-ads/sessions/${session.sessionId}") { bearerAuth(other.accessToken) }
        assertEquals(HttpStatusCode.NotFound, hidden.status)
        assertFalse(hidden.bodyAsText().contains(session.customData))
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/rewarded-ads/sessions/${session.sessionId}").status)
    }
    @Test fun `per account prepare and per IP status limits are enforced with retry header`() = testApplication {
        val f = AdMobFixture(); val auth = AuthenticationService(InMemoryAuthRepository(), passwordHasher = PasswordHasher(1000))
        val owner = runBlocking { assertIs<AuthResult.Success>(auth.register("admob-limits@example.com", "GoodPassword123!", "Owner", "test")).session }
        application { gameModule(authService = auth, adMobService = f.service, rateLimitPolicies = ServerRateLimitPolicies(
            adMobPreparePerPlayer = RateLimitPolicy(1, 60_000), adMobStatusPerIp = RateLimitPolicy(1, 60_000))) }
        suspend fun prepare() = client.post("/api/rewarded-ads/sessions") {
            bearerAuth(owner.accessToken); contentType(ContentType.Application.Json); setBody("""{"requestId":"request"}""")
        }
        assertEquals(HttpStatusCode.OK, prepare().status)
        val limited = prepare()
        assertEquals(HttpStatusCode.TooManyRequests, limited.status); assertNotNull(limited.headers[HttpHeaders.RetryAfter])
        assertEquals(HttpStatusCode.NotFound, client.get("/api/rewarded-ads/sessions/unknown") { bearerAuth(owner.accessToken) }.status)
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/api/rewarded-ads/sessions/unknown") { bearerAuth(owner.accessToken) }.status)
    }
    @Test fun `storage failure returns retryable safe 503`() = testApplication {
        val f = AdMobFixture(); val auth = AuthenticationService(InMemoryAuthRepository())
        val broken = object : AdMobRepository by f.repository {
            override suspend fun accept(event: VerifiedAdMobEvent, now: Long): AdMobSession? = throw SQLException("private callback storage")
        }
        val service = AdMobService(AdMobConfiguration(f.unit), broken, f.quota, f.verifier, { f.now }, rewardedAdAvailability = f.availability)
        application { gameModule(authService = auth, adMobService = service) }
        val session = runBlocking { service.prepare("owner", "sql-failure") }
        val response = client.get("/api/rewarded-ads/ssv?${f.signed(session.customData)}")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertFalse(response.bodyAsText().contains("private callback"))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }
    @Test fun `new session maintenance admission rejects while existing session and callback recover`() = runTest {
        val f = AdMobFixture(); var allowed = true
        val service = AdMobService(AdMobConfiguration(f.unit), f.repository, f.quota, f.verifier, { f.now }, { allowed }, rewardedAdAvailability = f.availability)
        val existing = service.prepare("owner", "request"); allowed = false
        assertEquals(existing, service.prepare("owner", "request"))
        assertFailsWith<AdMobUnavailable> { service.prepare("owner", "new") }
        service.callback(f.signed(existing.customData))
        assertEquals(12, service.status("owner", existing.sessionId)?.quota?.remainingMatches)
        val cancelled = AdMobService(AdMobConfiguration(f.unit), f.repository, f.quota,
            AdMobSsvVerifier(AdMobPublicKeys { throw CancellationException("cancel") }), { f.now }, rewardedAdAvailability = f.availability)
        assertFailsWith<CancellationException> { cancelled.callback(f.signed(existing.customData)) }
        assertEquals(12, f.quota.getSnapshot("owner", f.now).remainingMatches)
    }
}
