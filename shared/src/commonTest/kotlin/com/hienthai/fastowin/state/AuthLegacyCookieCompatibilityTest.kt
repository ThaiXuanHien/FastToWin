package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthLegacyCookieCompatibilityTest {
    @Test fun sameTabRefreshRetainsLiveResponseWhileCookieMetadataStaysCredentialFree() = runTest {
        val fixture = Fixture(this)
        val controller = fixture.controller()
        try {
            fixture.login(controller)
            assertEquals("web-access-2", controller.validAccessToken(true))
            assertEquals("web-access-2", controller.state.value.session?.accessToken)
            assertEquals("", fixture.store.load(URL)?.accessToken)
            assertEquals("", fixture.store.load(URL)?.refreshToken)
            assertNull(fixture.store.load(URL)?.refreshAttemptSecret)
            assertEquals(1, fixture.refreshes)
        } finally { controller.close(); fixture.close() }
    }

    @Test fun reloadedMetadataSessionInstallsLiveAccessTokenFromCookieRefresh() = runTest {
        val fixture = Fixture(this)
        val first = fixture.controller()
        try { fixture.login(first) } finally { first.close() }
        val reloaded = fixture.controller()
        try {
            assertEquals("", reloaded.state.value.session?.accessToken)
            assertEquals("web-access-2", reloaded.validAccessToken())
            assertEquals("web-access-2", reloaded.state.value.session?.accessToken)
            assertEquals(AuthStage.PLAYING, reloaded.state.value.stage)
            assertEquals("", fixture.store.load(URL)?.accessToken)
        } finally { reloaded.close(); fixture.close() }
    }

    @Test fun logoutAfterLoginSendsCookieRevocationDespiteStrippedStoredTokens() = runTest {
        val fixture = Fixture(this)
        val controller = fixture.controller()
        try {
            fixture.login(controller)
            controller.logout()
            advanceUntilIdle()
            assertEquals(1, fixture.logouts)
            assertFalse(fixture.cookieActive)
            assertNull(fixture.store.load(URL))
            assertNull(controller.state.value.session)
            assertEquals(AuthStage.LOGIN, controller.state.value.stage)
        } finally { controller.close(); fixture.close() }
    }

    @Test fun metadataEditPreservesLiveAccessTokenAndCookieOnlyStorage() = runTest {
        val fixture = Fixture(this)
        val controller = fixture.controller()
        try {
            fixture.login(controller)
            controller.updateStoredDisplayName("Edited")
            assertEquals("Edited", controller.state.value.session?.displayName)
            assertEquals("web-access-1", controller.state.value.session?.accessToken)
            assertEquals("Edited", fixture.store.load(URL)?.displayName)
            assertEquals("", fixture.store.load(URL)?.accessToken)
            assertNull(fixture.store.load(URL)?.refreshAttemptSecret)
        } finally { controller.close(); fixture.close() }
    }

    /** Mirrors WebStores.kt: persisted metadata strips both bearer credentials and access expiry. */
    private class MetadataOnlyStore : AuthSessionStore {
        var stored: StoredAuthSession? = null
        override fun load(serverUrl: String) = stored
        override fun save(serverUrl: String, session: StoredAuthSession) {
            stored = session.copy(accessToken = "", refreshToken = "", accessExpiresAtEpochMillis = 0L)
        }
        override fun clear(serverUrl: String) { stored = null }
    }

    private class Fixture(scope: TestScope) {
        val store = MetadataOnlyStore()
        var refreshes = 0
        var logouts = 0
        var cookieActive = false
        private var responses = 0
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        private val configurator = AuthRequestConfigurator { request ->
            request.header("X-FastToWin-Web-Session", "1")
            request.header("X-FastToWin-CSRF", "1")
            request.header(HttpHeaders.Cookie, "test_web_session=synthetic-cookie")
        }
        init { Dispatchers.setMain(dispatcher) }
        fun controller(): AuthController {
            val engine = MockEngine(MockEngineConfig().apply {
                dispatcher = this@Fixture.dispatcher
                addHandler { request ->
                    assertEquals("1", request.headers["X-FastToWin-Web-Session"])
                    assertEquals("1", request.headers["X-FastToWin-CSRF"])
                    assertNotNull(request.headers[HttpHeaders.Cookie])
                    when (request.url.encodedPath) {
                        "/auth/login" -> { cookieActive = true; sessionResponse(++responses) }
                        "/auth/refresh" -> {
                            refreshes++
                            assertTrue(cookieActive)
                            val refresh = request.decode<RefreshTokenRequest>()
                            assertEquals("", refresh.refreshToken)
                            assertNull(refresh.refreshAttemptSecret)
                            sessionResponse(++responses)
                        }
                        "/auth/logout" -> {
                            logouts++
                            val logout = request.decode<LogoutRequest>()
                            assertEquals("", logout.refreshToken)
                            assertNull(logout.refreshAttemptSecret)
                            cookieActive = false
                            respond("", HttpStatusCode.NoContent)
                        }
                        else -> error("Unexpected auth route")
                    }
                }
            })
            val api = AuthApiClient(URL, configurator, HttpClient(engine) { install(ContentNegotiation) { json(ProtocolJson) } })
            return AuthController(URL, store, InMemoryResumeTokenStore(), "web", api = api)
        }
        suspend fun login(controller: AuthController) {
            controller.login("fixture@example.invalid", "test-password")
            controller.state.first { it.session != null }
        }
        fun close() = Dispatchers.resetMain()
    }

    companion object {
        private const val URL = "http://cookie-compatibility.invalid"
        private val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        private inline fun <reified T> io.ktor.client.request.HttpRequestData.decode(): T =
            ProtocolJson.decodeFromString((body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
        private fun MockRequestHandleScope.sessionResponse(number: Int) = respond(
            """{"userId":"fixture","displayName":"Fixture","accessToken":"web-access-$number","refreshToken":"","accessExpiresAtEpochMillis":9223372036854775807,"refreshExpiresAtEpochMillis":9223372036854775807}""", headers = JSON)
    }
}
