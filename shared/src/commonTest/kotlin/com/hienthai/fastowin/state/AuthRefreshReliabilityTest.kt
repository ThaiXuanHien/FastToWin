package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.protocol.ProtocolJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRefreshReliabilityTest {
    @Test fun temporaryServerFailureKeepsSessionAndCanRefreshLater() = runTest {
        retryableFailure(HttpStatusCode.ServiceUnavailable)
    }

    @Test fun rateLimitKeepsSessionAndCanRefreshLater() = runTest {
        retryableFailure(HttpStatusCode.TooManyRequests)
    }

    @Test fun accountActionRefreshOutageShowsErrorWithoutLogoutOrStuckLoading() = runTest {
        val f = Fixture(this, expiredAccess = true) { respond("outage", HttpStatusCode.ServiceUnavailable) }
        try {
            f.controller.requestEmailVerification()
            f.controller.state.first { !it.isLoading }
            assertEquals(f.initial, f.store.load(URL))
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
            assertFalse(f.controller.state.value.isLoading)
            assertNotNull(f.controller.state.value.error)
        } finally { f.close() }
    }

    @Test fun sessionsRefreshOutageShowsErrorWithoutLogoutOrStuckLoading() = runTest {
        val f = Fixture(this, expiredAccess = true) { respond("outage", HttpStatusCode.ServiceUnavailable) }
        try {
            f.controller.loadSessions()
            f.controller.state.first { !it.areSessionsLoading }
            assertEquals(f.initial, f.store.load(URL))
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
            assertFalse(f.controller.state.value.areSessionsLoading)
            assertNotNull(f.controller.state.value.error)
        } finally { f.close() }
    }

    private suspend fun TestScope.retryableFailure(status: HttpStatusCode) {
        val f = Fixture(this) { count ->
            if (count == 1) respond("unavailable", status) else success()
        }
        try {
            assertFailsWith<AuthApiException> { f.controller.validAccessToken(true) }
            assertEquals(f.initial, f.store.load(URL))
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
            assertEquals("fresh-access", f.controller.validAccessToken(true))
            assertEquals("fresh-refresh", f.store.load(URL)?.refreshToken)
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
        } finally { f.close() }
    }

    @Test fun temporaryTransportFailureDoesNotDestroyStoredSession() = runTest {
        val f = Fixture(this) { count -> if (count == 1) throw TemporaryTransportFailure() else success() }
        try {
            assertFailsWith<TemporaryTransportFailure> { f.controller.validAccessToken(true) }
            assertEquals(f.initial, f.store.load(URL))
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
            assertEquals("fresh-access", f.controller.validAccessToken(true))
        } finally { f.close() }
    }

    @Test fun requestTimeoutKeepsSessionForTheNextRefresh() = runTest {
        val f = Fixture(this) { count -> if (count == 1) throw HttpRequestTimeoutException(URL, 1000L) else success() }
        try {
            assertFailsWith<HttpRequestTimeoutException> { f.controller.validAccessToken(true) }
            assertEquals(f.initial, f.store.load(URL))
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
            assertEquals("fresh-access", f.controller.validAccessToken(true))
        } finally { f.close() }
    }

    @Test fun cancelledRefreshKeepsSession() = runTest {
        val f = Fixture(this) { throw CancellationException("Refresh cancelled") }
        try {
            assertFailsWith<CancellationException> { f.controller.validAccessToken(true) }
            assertEquals(f.initial, f.store.load(URL))
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
        } finally { f.close() }
    }

    @Test fun rejectedRefreshClearsSession() = runTest {
        rejectedRefresh(HttpStatusCode.Unauthorized, """{"code":"INVALID_REFRESH_TOKEN","message":"Revoked"}""")
    }

    @Test fun unauthorizedWithoutJsonStillClearsSession() = runTest {
        rejectedRefresh(HttpStatusCode.Unauthorized, "not json")
    }

    @Test fun blockedAccountClearsSession() = runTest {
        rejectedRefresh(HttpStatusCode.BadRequest, """{"code":"ACCOUNT_BLOCKED","message":"Blocked"}""")
    }

    private suspend fun TestScope.rejectedRefresh(status: HttpStatusCode, body: String) {
        val f = Fixture(this) { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
        try {
            assertNull(f.controller.validAccessToken(true))
            assertNull(f.store.load(URL))
            assertNull(f.controller.state.value.session)
            assertEquals(AuthStage.LOGIN, f.controller.state.value.stage)
        } finally { f.close() }
    }

    @Test fun lateTemporaryRefreshFailureCannotResurrectLogout() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val f = Fixture(this) { entered.complete(Unit); release.await(); respond("outage", HttpStatusCode.ServiceUnavailable) }
        try {
            val pending = async { f.controller.validAccessToken(true) }
            entered.await()
            f.controller.logout()
            release.complete(Unit)
            assertNull(pending.await())
            assertNull(f.store.load(URL))
            assertEquals(AuthStage.LOGIN, f.controller.state.value.stage)
        } finally { f.close() }
    }

    private class TemporaryTransportFailure : Exception("Synthetic network outage")

    private class Fixture(scope: TestScope, expiredAccess: Boolean = false, handler: suspend MockRequestHandleScope.(Int) -> io.ktor.client.request.HttpResponseData) {
        val initial = StoredAuthSession("fixture", "fixture@example.invalid", "Fixture", "old-access", "old-refresh", if (expiredAccess) 0L else Long.MAX_VALUE, Long.MAX_VALUE)
        val store = InMemoryAuthSessionStore().apply { save(URL, initial) }
        private var count = 0
        private val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/auth/logout") respond("", HttpStatusCode.NoContent)
            else { assertEquals("/auth/refresh", request.url.encodedPath); handler(++count) }
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val controller: AuthController
        init {
            Dispatchers.setMain(StandardTestDispatcher(scope.testScheduler))
            controller = AuthController(URL, store, InMemoryResumeTokenStore(), "test", api = AuthApiClient(URL, client = client))
        }
        fun close() { controller.close(); Dispatchers.resetMain() }
    }

    companion object {
        private const val URL = "http://refresh-test.invalid"
        private fun MockRequestHandleScope.success() = respond(
            """{"userId":"fixture","displayName":"Fixture","accessToken":"fresh-access","refreshToken":"fresh-refresh","accessExpiresAtEpochMillis":9223372036854775807,"refreshExpiresAtEpochMillis":9223372036854775807}""",
            headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }
}
