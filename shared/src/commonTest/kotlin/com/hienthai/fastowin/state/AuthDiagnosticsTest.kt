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
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthDiagnosticsTest {
    @Test fun successfulRefreshRecordsResponseBeforeSavingWithoutLoggingCredentials() = runTest {
        val f = Fixture(this)
        try {
            assertEquals("private-fresh-access", f.controller.validAccessToken())
            assertEquals(listOf(AuthDiagnosticPhase.CONTROLLER_CREATED, AuthDiagnosticPhase.REFRESH_STARTED,
                AuthDiagnosticPhase.HTTP_RESPONSE, AuthDiagnosticPhase.REFRESH_STORED), f.events.map { it.phase })
            val received = f.events.single { it.phase == AuthDiagnosticPhase.HTTP_RESPONSE }
            assertEquals(200, received.httpStatus)
            assertEquals("railway_fixture_123", received.requestId)
            assertEquals(0, received.credentialGeneration)
            val stored = f.events.last()
            assertEquals(1, stored.credentialGeneration)
            assertEquals(received.attempt, stored.attempt)
            assertTrue(f.events.all { it.controllerId == received.controllerId })
            assertFalse(received.controllerId.contains("fixture-account"))
            assertEquals("private-fresh-refresh", f.store.load(URL)?.refreshToken)
            val lines = mutableListOf<String>()
            val local = localAuthDiagnosticSink(true, lines::add)
            f.events.forEach(local::record)
            for (secret in listOf("private-old-access", "private-old-refresh", "private-fresh-access",
                "private-fresh-refresh", "fixture-account", "private-email@example.invalid", "private-name")) {
                assertFalse(lines.joinToString().contains(secret), "Diagnostic output leaked a credential or account field")
            }
        } finally { f.close() }
    }

    @Test fun lostResponseAndRejectedRetryAreDistinguishableWithoutChangingLogoutPolicy() = runTest {
        val f = Fixture(this) { count ->
            if (count == 1) throw HttpRequestTimeoutException(URL, 10_000L)
            reject()
        }
        try {
            assertFailsWith<HttpRequestTimeoutException> { f.controller.validAccessToken() }
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
            assertEquals(f.initial, f.store.load(URL))
            assertNull(f.controller.validAccessToken())
            assertEquals(AuthStage.LOGIN, f.controller.state.value.stage)
            assertNull(f.store.load(URL))
            val starts = f.events.filter { it.phase == AuthDiagnosticPhase.REFRESH_STARTED }
            assertEquals(2, starts.size)
            assertNotEquals(starts[0].attempt, starts[1].attempt)
            assertEquals(listOf(0, 0), starts.map { it.credentialGeneration })
            assertEquals(AuthDiagnosticFailure.TIMEOUT,
                f.events.first { it.phase == AuthDiagnosticPhase.REFRESH_FAILED }.failure)
            assertEquals(listOf(401), f.events.filter { it.phase == AuthDiagnosticPhase.HTTP_RESPONSE }.map { it.httpStatus })
            assertEquals(AuthExpirySource.REFRESH_REJECTED, f.events.last().expirySource)
            assertEquals(AuthDiagnosticRejection.INVALID_REFRESH_TOKEN,
                f.events.last { it.phase == AuthDiagnosticPhase.REFRESH_FAILED }.rejection)
        } finally { f.close() }
    }

    @Test fun cancellingOneScreenWaiterStillRecordsTheSharedRefreshAsStored() = runTest {
        val accepted = CompletableDeferred<Unit>()
        val deliver = CompletableDeferred<Unit>()
        val f = Fixture(this) { accepted.complete(Unit); deliver.await(); success() }
        try {
            val first = launch { f.controller.validAccessToken() }
            accepted.await()
            val second = async { f.controller.validAccessToken(true) }
            runCurrent()
            first.cancelAndJoin()
            deliver.complete(Unit)
            assertEquals("private-fresh-access", second.await())
            assertEquals(1, f.events.count { it.phase == AuthDiagnosticPhase.REFRESH_STARTED })
            assertEquals(1, f.events.count { it.phase == AuthDiagnosticPhase.REFRESH_STORED })
            assertFalse(f.events.any { it.phase == AuthDiagnosticPhase.REFRESH_CANCELLED })
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
        } finally { deliver.complete(Unit); f.close() }
    }

    @Test fun closingControllerRecordsItsCancellationWithoutExpiringTheSession() = runTest {
        val accepted = CompletableDeferred<Unit>()
        val f = Fixture(this) { accepted.complete(Unit); awaitCancellation() }
        try {
            val pending = async { f.controller.validAccessToken() }
            accepted.await()
            f.controller.close()
            runCurrent()
            assertTrue(pending.isCancelled)
            assertTrue(f.events.any { it.phase == AuthDiagnosticPhase.CONTROLLER_CLOSED })
            assertTrue(f.events.any { it.phase == AuthDiagnosticPhase.REFRESH_CANCELLED })
            assertFalse(f.events.any { it.phase == AuthDiagnosticPhase.SESSION_EXPIRED })
            assertEquals(f.initial, f.store.load(URL))
        } finally { f.close() }
    }

    @Test fun rejectedRefreshRecordsOnlyAllowlistedFailureMetadata() = runTest {
        val f = Fixture(this) { reject() }
        try {
            assertNull(f.controller.validAccessToken())
            assertEquals(AuthStage.LOGIN, f.controller.state.value.stage)
            assertTrue(f.events.any { it.phase == AuthDiagnosticPhase.SESSION_EXPIRED })
            assertEquals(AuthExpirySource.REFRESH_REJECTED, f.events.last().expirySource)
            assertEquals(AuthDiagnosticFailure.HTTP, f.events.last { it.failure != null }.failure)
            val lines = mutableListOf<String>()
            f.events.forEach(localAuthDiagnosticSink(true, lines::add)::record)
            assertFalse(lines.joinToString().contains("private-server-error"))
        } finally { f.close() }
    }

    @Test fun storageFailureHasADifferentExpirySourceFromServerRejection() = runTest {
        val f = Fixture(this, failSave = true)
        try {
            assertNull(f.controller.validAccessToken())
            assertEquals(AuthStage.LOGIN, f.controller.state.value.stage)
            assertTrue(f.events.any { it.phase == AuthDiagnosticPhase.SESSION_EXPIRED })
            assertEquals(AuthExpirySource.SESSION_STORAGE, f.events.last().expirySource)
            assertEquals(AuthDiagnosticFailure.STORAGE, f.events.last { it.failure != null }.failure)
            assertFalse(f.events.any { it.phase == AuthDiagnosticPhase.REFRESH_STORED })
        } finally { f.close() }
    }

    @Test fun diagnosticWriterFailureCannotTurnSuccessfulRefreshIntoLogout() = runTest {
        val f = Fixture(this, sink = AuthDiagnosticSink { error("private-log-writer-failure") })
        try {
            assertEquals("private-fresh-access", f.controller.validAccessToken())
            assertEquals(AuthStage.PLAYING, f.controller.state.value.stage)
            assertEquals("private-fresh-refresh", f.store.load(URL)?.refreshToken)
        } finally { f.close() }
    }

    @Test fun localLoggingIsOffUnlessExplicitlyEnabled() = runTest {
        val lines = mutableListOf<String>()
        val f = Fixture(this, sink = localAuthDiagnosticSink(false, lines::add))
        try {
            assertEquals("private-fresh-access", f.controller.validAccessToken())
            assertTrue(lines.isEmpty())
        } finally { f.close() }
    }

    @Test fun invalidCorrelationHeaderIsOmittedFromLocalOutput() = runTest {
        val f = Fixture(this) { success("private-email@example.invalid unsafe header") }
        try {
            assertEquals("private-fresh-access", f.controller.validAccessToken())
            assertTrue(f.events.any { it.phase == AuthDiagnosticPhase.HTTP_RESPONSE })
            assertNull(f.events.single { it.phase == AuthDiagnosticPhase.HTTP_RESPONSE }.requestId)
        } finally { f.close() }
    }

    @Test fun gameConnectionExpiryIsIdentifiedWithoutLoggingItsMessage() = runTest {
        val f = Fixture(this)
        try {
            f.controller.expireSession("private-game-error", AuthExpirySource.GAME_CONNECTION)
            assertEquals(AuthStage.LOGIN, f.controller.state.value.stage)
            assertNull(f.store.load(URL))
            assertTrue(f.events.any { it.phase == AuthDiagnosticPhase.SESSION_EXPIRED })
            assertEquals(AuthExpirySource.GAME_CONNECTION, f.events.last().expirySource)
            assertEquals(AuthDiagnosticPhase.SESSION_EXPIRED, f.events.last().phase)
        } finally { f.close() }
    }

    private class Fixture(
        scope: TestScope,
        failSave: Boolean = false,
        sink: AuthDiagnosticSink? = null,
        handler: suspend MockRequestHandleScope.(Int) -> io.ktor.client.request.HttpResponseData = { success() }
    ) {
        val initial = StoredAuthSession("fixture-account", "private-email@example.invalid", "private-name",
            "private-old-access", "private-old-refresh", 0L, Long.MAX_VALUE)
        private val memory = InMemoryAuthSessionStore().apply { save(URL, initial) }
        val store = object : AuthSessionStore {
            override fun load(serverUrl: String) = memory.load(serverUrl)
            override fun save(serverUrl: String, session: StoredAuthSession) {
                if (failSave) throw AuthSessionStoreException("SESSION_STORE_FAILED", "private-storage-error")
                memory.save(serverUrl, session)
            }
            override fun clear(serverUrl: String) = memory.clear(serverUrl)
        }
        val events = mutableListOf<AuthDiagnosticEvent>()
        private var count = 0
        private val client = HttpClient(MockEngine { handler(++count) }) {
            install(ContentNegotiation) { json(ProtocolJson) }
        }
        val controller: AuthController
        init {
            Dispatchers.setMain(StandardTestDispatcher(scope.testScheduler))
            controller = AuthController(URL, store, InMemoryResumeTokenStore(), "test", api = AuthApiClient(URL, client = client),
                diagnosticSink = sink ?: AuthDiagnosticSink(events::add))
        }
        fun close() { controller.close(); Dispatchers.resetMain() }
    }

    companion object {
        private const val URL = "http://auth-diagnostics.invalid"
        private fun MockRequestHandleScope.success(requestId: String = "railway_fixture_123") = respond(
            """{"userId":"fixture-account","displayName":"private-name","accessToken":"private-fresh-access","refreshToken":"private-fresh-refresh","accessExpiresAtEpochMillis":9223372036854775807,"refreshExpiresAtEpochMillis":9223372036854775807}""",
            headers = headersOf(HttpHeaders.ContentType to listOf("application/json"), "x-railway-request-id" to listOf(requestId)))
        private fun MockRequestHandleScope.reject() = respond(
            """{"code":"INVALID_REFRESH_TOKEN","message":"private-server-error"}""", HttpStatusCode.Unauthorized,
            headers = headersOf(HttpHeaders.ContentType to listOf("application/json"), "x-railway-request-id" to listOf("railway_rejected_456")))
    }
}
