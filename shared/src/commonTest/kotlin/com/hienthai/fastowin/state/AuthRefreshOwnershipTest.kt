package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RefreshTokenRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRefreshOwnershipTest {
    @Test fun cancelledRouteDoesNotDiscardAnAlreadyRotatedCredential() = runTest {
        val f = Fixture(this)
        try {
            val route = launch { f.controller.validAccessToken() }
            f.accepted.await()
            route.cancelAndJoin()
            f.deliver.complete(Unit)

            assertEquals("fresh-access", f.controller.validAccessToken())
            assertEquals(listOf("old-refresh"), f.refreshTokens)
            assertEquals("fresh-refresh", f.store.load(URL)?.refreshToken)
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
        } finally { f.close() }
    }

    @Test fun oneCancelledWaiterDoesNotCancelTheOtherOrStartASecondRefresh() = runTest {
        val f = Fixture(this)
        try {
            val route = launch { f.controller.validAccessToken(true) }
            f.accepted.await()
            val socket = async { f.controller.validAccessToken(true) }
            runCurrent()
            route.cancelAndJoin()
            f.deliver.complete(Unit)

            assertEquals("fresh-access", socket.await())
            assertEquals(listOf("old-refresh"), f.refreshTokens)
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
        } finally { f.close() }
    }

    @Test fun sharedTemporaryFailureKeepsCredentialsAndAllowsTheNextAttempt() = runTest {
        val f = Fixture(this, firstStatus = HttpStatusCode.ServiceUnavailable)
        try {
            val first = async { runCatching { f.controller.validAccessToken() } }
            f.accepted.await()
            val second = async { runCatching { f.controller.validAccessToken(true) } }
            runCurrent()
            f.deliver.complete(Unit)

            for (result in listOf(first.await(), second.await())) {
                assertEquals(503, (result.exceptionOrNull() as? AuthApiException)?.httpStatus)
            }
            assertEquals(listOf("old-refresh"), f.refreshTokens)
            assertEquals(f.initial, f.store.load(URL))
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
            assertEquals("fresh-access", f.controller.validAccessToken())
            assertEquals(2, f.refreshTokens.size)
        } finally { f.close() }
    }

    @Test fun logoutRejectsALateSuccessfulRefreshEvenIfTransportIgnoresCancellation() = runTest {
        val f = Fixture(this, nonCooperativeResponse = true)
        try {
            val pending = async { f.controller.validAccessToken() }
            f.accepted.await()
            f.controller.logout()
            f.deliver.complete(Unit)

            assertNull(pending.await())
            assertNull(f.store.load(URL))
            assertNull(f.controller.state.value.session)
            assertEquals(AuthStage.LOGIN, f.controller.state.value.stage)
        } finally { f.close() }
    }

    @Test fun closingTheControllerCancelsItsPendingRefreshWithoutExpiringTheAccount() = runTest {
        val f = Fixture(this)
        var pending: Deferred<String?>? = null
        try {
            pending = async { f.controller.validAccessToken() }
            f.accepted.await()
            f.controller.close()
            runCurrent()

            assertTrue(pending.isCancelled, "Refresh must belong to the closed controller")
            assertEquals(f.initial, f.store.load(URL))
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
        } finally {
            pending?.cancelAndJoin()
            f.close()
        }
    }

    private class Fixture(
        scope: TestScope,
        private val firstStatus: HttpStatusCode = HttpStatusCode.OK,
        private val nonCooperativeResponse: Boolean = false
    ) {
        val initial = StoredAuthSession("fixture", "fixture@example.invalid", "Fixture",
            "old-access", "old-refresh", 0L, Long.MAX_VALUE)
        val store = InMemoryAuthSessionStore().apply { save(URL, initial) }
        val accepted = CompletableDeferred<Unit>()
        val deliver = CompletableDeferred<Unit>()
        val refreshTokens = mutableListOf<String>()
        private var oldRefreshConsumed = false
        private val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/auth/logout") return@MockEngine respond("", HttpStatusCode.NoContent)
            assertEquals("/auth/refresh", request.url.encodedPath)
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            val token = ProtocolJson.decodeFromString<RefreshTokenRequest>(body).refreshToken
            refreshTokens += token
            val first = refreshTokens.size == 1
            if (token != "old-refresh" || oldRefreshConsumed) {
                respond("""{"code":"INVALID_REFRESH_TOKEN","message":"Already consumed"}""",
                    HttpStatusCode.Unauthorized, jsonHeaders)
            } else {
                // The server consumes a rotating credential before its response reaches the UI.
                if (!first || firstStatus == HttpStatusCode.OK) oldRefreshConsumed = true
                if (first) {
                    accepted.complete(Unit)
                    if (nonCooperativeResponse) withContext(NonCancellable) { deliver.await() }
                    else deliver.await()
                }
                if (first && firstStatus != HttpStatusCode.OK) respond("unavailable", firstStatus)
                else respond("""{"userId":"fixture","displayName":"Fixture","accessToken":"fresh-access","refreshToken":"fresh-refresh","accessExpiresAtEpochMillis":9223372036854775807,"refreshExpiresAtEpochMillis":9223372036854775807}""",
                    headers = jsonHeaders)
            }
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val controller: AuthController
        init {
            Dispatchers.setMain(StandardTestDispatcher(scope.testScheduler))
            controller = AuthController(URL, store, InMemoryResumeTokenStore(), "test",
                api = AuthApiClient(URL, client = client))
        }
        fun close() {
            deliver.complete(Unit)
            controller.close()
            Dispatchers.resetMain()
        }
    }

    companion object {
        private const val URL = "http://refresh-ownership.invalid"
        private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
