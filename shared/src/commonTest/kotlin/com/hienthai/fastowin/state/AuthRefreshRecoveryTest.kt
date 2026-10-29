package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRefreshRecoveryTest {
    @Test fun lostCommittedResponseReusesDurablePairAfterControllerRecreation() = runTest {
        val requests = mutableListOf<RefreshTokenRequest>()
        var committed: RefreshTokenRequest? = null
        val store = RecoveryStore()
        fun client() = api { request ->
            val refresh = request.decode<RefreshTokenRequest>()
            requests += refresh
            if (committed == null) {
                committed = refresh
                throw DroppedResponse()
            }
            if (refresh.refreshAttemptSecret != null && refresh == committed) success()
            else respond("""{"code":"INVALID_REFRESH_TOKEN","message":"Consumed"}""", HttpStatusCode.Unauthorized, JSON)
        }
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val first = controller(store, client())
        try {
            assertFailsWith<DroppedResponse> { first.validAccessToken(true) }
            assertEquals(1, requests.size)
            val pending = assertNotNull(store.load(URL)?.refreshAttemptSecret)
            first.close()
            val recreated = controller(store, client())
            try {
                assertEquals("fresh-access", recreated.validAccessToken(true))
                assertEquals(pending, requests.last().refreshAttemptSecret)
                assertEquals(requests.first(), requests.last())
                assertNull(store.load(URL)?.refreshAttemptSecret)
                assertEquals("fresh-refresh", store.load(URL)?.refreshToken)
                assertEquals(AuthStage.PLAYING, recreated.state.value.stage)
            } finally { recreated.close() }
        } finally { first.close(); Dispatchers.resetMain() }
    }

    @Test fun failedPendingPersistenceSendsNoHttp() = runTest {
        val store = RecoveryStore().apply { failPrepare = true }
        var requests = 0
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller(store, api { requests++; success() })
        try {
            assertNull(controller.validAccessToken(true))
            assertEquals(0, requests)
            assertEquals(AuthStage.LOGIN, controller.state.value.stage)
        } finally { controller.close(); Dispatchers.resetMain() }
    }

    @Test fun temporaryFailuresBeforeCommitKeepTheSamePairForRetry() = runTest {
        for (status in listOf(408, 429, 500, 503, null)) {
            val store = RecoveryStore()
            val requests = mutableListOf<RefreshTokenRequest>()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val controller = controller(store, api { request ->
                requests += request.decode<RefreshTokenRequest>()
                if (requests.size > 1) success()
                else if (status == null) throw HttpRequestTimeoutException(URL, 1000L)
                else respond("temporary", HttpStatusCode.fromValue(status))
            })
            try {
                assertNotNull(runCatching { controller.validAccessToken(true) }.exceptionOrNull())
                assertEquals(AuthStage.PLAYING, controller.state.value.stage)
                assertEquals("old-refresh", store.load(URL)?.refreshToken)
                assertNotNull(store.load(URL)?.refreshAttemptSecret)
                assertEquals("fresh-access", controller.validAccessToken(true))
                assertEquals(requests.first(), requests.last())
                assertNull(store.load(URL)?.refreshAttemptSecret)
            } finally { controller.close(); Dispatchers.resetMain() }
        }
    }

    @Test fun metadataWrittenDuringPendingSurvivesCredentialCompletion() = runTest {
        val store = RecoveryStore()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller(store, api { entered.complete(Unit); release.await(); success() })
        try {
            val refresh = async { controller.validAccessToken(true) }
            entered.await()
            val attempt = assertNotNull(store.load(URL)?.refreshAttemptSecret)
            controller.updateStoredDisplayName("Edited")
            assertEquals(attempt, store.load(URL)?.refreshAttemptSecret)
            release.complete(Unit)
            assertEquals("fresh-access", refresh.await())
            assertEquals("Edited", store.load(URL)?.displayName)
            assertEquals("Edited", controller.state.value.session?.displayName)
            assertNull(store.load(URL)?.refreshAttemptSecret)
        } finally { controller.close(); Dispatchers.resetMain() }
    }

    @Test fun emailVerificationWriterPreservesPendingAndCompletionKeepsVerification() = runTest {
        val store = RecoveryStore().apply { session = INITIAL.copy(accessExpiresAtEpochMillis = Long.MAX_VALUE, emailVerified = false) }
        val verificationEntered = CompletableDeferred<Unit>()
        val refreshEntered = CompletableDeferred<Unit>()
        val verify = CompletableDeferred<Unit>()
        val refreshResponse = CompletableDeferred<Unit>()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller(store, api { request ->
            when (request.url.encodedPath) {
                "/auth/email-verification/request" -> {
                    verificationEntered.complete(Unit); verify.await()
                    respond("""{"code":"EMAIL_VERIFIED","message":"Verified","emailVerified":true}""", headers = JSON)
                }
                else -> { refreshEntered.complete(Unit); refreshResponse.await(); success() }
            }
        })
        try {
            controller.requestEmailVerification()
            verificationEntered.await()
            val refresh = async { controller.validAccessToken(true) }
            refreshEntered.await()
            val attempt = assertNotNull(store.load(URL)?.refreshAttemptSecret)
            verify.complete(Unit)
            controller.state.first { it.session?.emailVerified == true }
            assertTrue(store.load(URL)?.emailVerified == true)
            assertEquals(attempt, store.load(URL)?.refreshAttemptSecret)
            refreshResponse.complete(Unit)
            assertEquals("fresh-access", refresh.await())
            assertTrue(store.load(URL)?.emailVerified == true)
            assertTrue(controller.state.value.session?.emailVerified == true)
        } finally { controller.close(); Dispatchers.resetMain() }
    }

    @Test fun logoutSendsDurablePendingSnapshotAndLateResponseCannotSave() = runTest {
        val store = RecoveryStore()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var logout: LogoutRequest? = null
        val logoutDelivered = CompletableDeferred<Unit>()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller(store, api { request ->
            if (request.url.encodedPath == "/auth/logout") {
                logout = request.decode<LogoutRequest>()
                logoutDelivered.complete(Unit)
                respond("", HttpStatusCode.NoContent)
            } else { entered.complete(Unit); withContext(NonCancellable) { release.await() }; success() }
        })
        try {
            val refresh = async { controller.validAccessToken(true) }
            entered.await()
            val pending = assertNotNull(store.load(URL))
            controller.logout()
            assertNull(controller.state.value.session)
            assertEquals(AuthStage.LOGIN, controller.state.value.stage)
            assertNull(store.load(URL))
            logoutDelivered.await()
            val logoutRequest = assertNotNull(logout)
            assertEquals(pending.refreshToken, logoutRequest.refreshToken)
            assertNotNull(logoutRequest.refreshAttemptSecret)
            assertEquals(pending.refreshAttemptSecret, logoutRequest.refreshAttemptSecret)
            release.complete(Unit)
            assertNull(refresh.await())
            assertNull(store.load(URL))
        } finally { controller.close(); Dispatchers.resetMain() }
    }

    @Test fun replacementLoginFencesLateRefreshSuccessAndRejection() = runTest {
        for (status in listOf(HttpStatusCode.OK, HttpStatusCode.Unauthorized)) {
            val store = RecoveryStore()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val controller = controller(store, api { request ->
                if (request.url.encodedPath == "/auth/login") success("login-access", "login-refresh")
                else {
                    entered.complete(Unit); release.await()
                    if (status == HttpStatusCode.OK) success()
                    else respond("""{"code":"INVALID_REFRESH_TOKEN","message":"Revoked"}""", status, JSON)
                }
            })
            try {
                val refresh = async { controller.validAccessToken(true) }
                entered.await()
                controller.login("fixture@example.invalid", "test-password")
                controller.state.first { it.session?.refreshToken == "login-refresh" }
                assertEquals("login-refresh", store.load(URL)?.refreshToken)
                controller.updateStoredDisplayName("New login")
                release.complete(Unit)
                assertNull(refresh.await())
                assertEquals("login-refresh", store.load(URL)?.refreshToken)
                assertEquals("New login", store.load(URL)?.displayName)
                assertEquals(AuthStage.PLAYING, controller.state.value.stage)
            } finally { controller.close(); Dispatchers.resetMain() }
        }
    }

    @Test fun staleControllerMetadataCannotOverwriteNewStoredGeneration() = runTest {
        val store = RecoveryStore()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller(store, api { success() })
        try {
            store.save(URL, INITIAL.copy(accessToken = "login-access", refreshToken = "login-refresh", displayName = "New owner"))
            controller.updateStoredDisplayName("Stale edit")
            assertEquals("login-refresh", store.load(URL)?.refreshToken)
            assertEquals("New owner", store.load(URL)?.displayName)
        } finally { controller.close(); Dispatchers.resetMain() }
    }

    @Test fun concurrentScreenWaitersShareOnePreparedRequestEvenWhenOneCancels() = runTest {
        val store = RecoveryStore()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller(store, api { requests++; entered.complete(Unit); release.await(); success() })
        try {
            val first = launch { controller.validAccessToken(true) }
            entered.await()
            val second = async { controller.validAccessToken(true) }
            runCurrent()
            first.cancelAndJoin()
            release.complete(Unit)
            assertEquals("fresh-access", second.await())
            assertEquals(1, requests)
            assertEquals(1, store.prepares)
            assertNull(store.load(URL)?.refreshAttemptSecret)
        } finally { controller.close(); Dispatchers.resetMain() }
    }

    @Test fun legacyStoreAndBothExistingCallbackCallStylesRemainCompatible() = runTest {
        val store: AuthSessionStore = InMemoryAuthSessionStore().apply { save(URL, INITIAL) }
        val requests = mutableListOf<RefreshTokenRequest>()
        var callbacks = 0
        val api = api { request -> requests += request.decode<RefreshTokenRequest>(); success() }
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller(store, api)
        try {
            api.refresh("legacy", { _, _ -> callbacks++ })
            api.refresh("legacy") { _, _ -> callbacks++ }
            assertEquals(2, callbacks)
            assertEquals("fresh-access", controller.validAccessToken(true))
            assertTrue(requests.all { it.refreshAttemptSecret == null })
            assertNull(ProtocolJson.decodeFromString<StoredAuthSession>(
                """{"userId":"fixture","email":"fixture@example.invalid","displayName":"Fixture","accessToken":"old-access","refreshToken":"old-refresh","accessExpiresAtEpochMillis":0,"refreshExpiresAtEpochMillis":9223372036854775807}""").refreshAttemptSecret)
        } finally { controller.close(); Dispatchers.resetMain() }
    }

    @Test fun repeatedLogoutCannotClearAReplacementStoredOwner() = runTest {
        val store = RecoveryStore()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller(store, api { respond("", HttpStatusCode.NoContent) })
        try {
            controller.logout()
            val replacement = INITIAL.copy(accessToken = "login-access", refreshToken = "login-refresh")
            store.save(URL, replacement)
            controller.logout()
            assertEquals(replacement, store.load(URL))
        } finally { controller.close(); Dispatchers.resetMain() }
    }

    @Test fun verificationResponseForPreviousLoginCannotEditTheNewOwner() = runTest {
        val store = RecoveryStore().apply { session = INITIAL.copy(accessExpiresAtEpochMillis = Long.MAX_VALUE, emailVerified = false) }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller(store, api { request ->
            if (request.url.encodedPath == "/auth/login") success("login-access", "login-refresh")
            else {
                entered.complete(Unit); release.await()
                respond("""{"message":"Verified","emailVerified":true}""", headers = JSON)
            }
        })
        try {
            controller.requestEmailVerification()
            entered.await()
            controller.logout()
            controller.login("fixture@example.invalid", "test-password")
            controller.state.first { it.session?.refreshToken == "login-refresh" }
            // The replacement is deliberately unverified even though the old request succeeds.
            val replacement = assertNotNull(store.load(URL)).copy(emailVerified = false)
            store.save(URL, replacement)
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(replacement, store.load(URL))
        } finally { controller.close(); Dispatchers.resetMain() }
    }

    @Test fun lateRevocationActionCannotClearReplacementLogin() = runTest {
        val actions: List<(AuthController) -> Unit> = listOf(
            { it.changePassword("old-password", "new-password") },
            { it.deleteAccount("test-password") },
            { it.revokeAllSessions() }
        )
        for (action in actions) {
            val store = RecoveryStore().apply { session = INITIAL.copy(accessExpiresAtEpochMillis = Long.MAX_VALUE) }
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val controller = controller(store, api { request ->
                when (request.url.encodedPath) {
                    "/auth/login" -> success("login-access", "login-refresh")
                    "/auth/logout" -> respond("", HttpStatusCode.NoContent)
                    else -> { entered.complete(Unit); release.await(); respond("""{"message":"Done"}""", headers = JSON) }
                }
            })
            try {
                action(controller)
                entered.await()
                controller.logout()
                controller.login("fixture@example.invalid", "test-password")
                controller.state.first { it.session?.refreshToken == "login-refresh" }
                release.complete(Unit)
                advanceUntilIdle()
                assertEquals("login-refresh", store.load(URL)?.refreshToken)
                assertEquals(AuthStage.PLAYING, controller.state.value.stage)
            } finally { controller.close(); Dispatchers.resetMain() }
        }
    }

    @Test fun refreshRequiredBeforeAnActionStillClearsItsOwnGeneration() = runTest {
        val actions: List<Pair<(AuthController) -> Unit, AuthStage>> = listOf(
            { controller: AuthController -> controller.changePassword("old-password", "new-password") } to AuthStage.LOGIN,
            { controller: AuthController -> controller.deleteAccount("test-password") } to AuthStage.WELCOME,
            { controller: AuthController -> controller.revokeAllSessions() } to AuthStage.LOGIN
        )
        for ((action, expectedStage) in actions) {
            val store = RecoveryStore()
            val paths = mutableListOf<String>()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val controller = controller(store, api { request ->
                paths += request.url.encodedPath
                if (request.url.encodedPath == "/auth/refresh") success()
                else respond("""{"message":"Done"}""", headers = JSON)
            })
            try {
                action(controller)
                controller.state.first { it.session == null }
                assertNull(store.load(URL))
                assertEquals(expectedStage, controller.state.value.stage)
                assertEquals(2, paths.size)
                assertEquals("/auth/refresh", paths.first())
            } finally { controller.close(); Dispatchers.resetMain() }
        }
    }

    @Test fun initialExpiredSnapshotCannotClearANewerStoredOwner() = runTest {
        val replacement = INITIAL.copy(accessToken = "login-access", refreshToken = "login-refresh")
        val store = RecoveryStore().apply {
            session = INITIAL.copy(refreshExpiresAtEpochMillis = 0L)
            onLoad = { session = replacement }
        }
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller(store, api { success() })
        try { assertEquals(replacement, store.load(URL)) }
        finally { controller.close(); Dispatchers.resetMain() }
    }

    @Test fun logoutStillClearsUiAndSendsPendingRevocationWhenLocalClearFails() = runTest {
        val store = RecoveryStore().apply { failClear = true }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val loggedOut = CompletableDeferred<LogoutRequest>()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = controller(store, api { request ->
            if (request.url.encodedPath == "/auth/logout") {
                loggedOut.complete(request.decode<LogoutRequest>())
                respond("", HttpStatusCode.NoContent)
            } else { entered.complete(Unit); release.await(); success() }
        })
        try {
            val refresh = async { controller.validAccessToken(true) }
            entered.await()
            val pending = assertNotNull(store.load(URL))
            val failure = runCatching { controller.logout() }.exceptionOrNull()
            assertNull(controller.state.value.session)
            assertEquals(AuthStage.LOGIN, controller.state.value.stage)
            assertNull(failure)
            val logout = loggedOut.await()
            assertEquals(pending.refreshAttemptSecret, logout.refreshAttemptSecret)
            release.complete(Unit)
            assertNull(refresh.await())
        } finally { controller.close(); Dispatchers.resetMain() }
    }

    private class DroppedResponse : Exception("Synthetic response loss")

    private class RecoveryStore : RecoverableAuthSessionStore {
        var session: StoredAuthSession? = INITIAL
        var failPrepare = false
        var failClear = false
        var prepares = 0
        var onLoad: (() -> Unit)? = null
        override fun load(serverUrl: String): StoredAuthSession? {
            val current = session
            val action = onLoad
            onLoad = null
            action?.invoke()
            return current
        }
        override fun save(serverUrl: String, session: StoredAuthSession) { this.session = session }
        override fun clear(serverUrl: String) { session = null }
        override fun prepareRefresh(serverUrl: String, expected: StoredAuthSession): StoredAuthSession? {
            prepares++
            if (failPrepare) throw AuthSessionStoreException("TEST_STORAGE_FAILED", "Synthetic persistence failure")
            val current = session ?: return null
            if (current.userId != expected.userId || current.refreshToken != expected.refreshToken) return null
            return current.copy(refreshAttemptSecret = current.refreshAttemptSecret ?: "deterministic-test-attempt").also { session = it }
        }
        override fun completeRefresh(serverUrl: String, expected: StoredAuthSession, refreshed: StoredAuthSession): Boolean {
            val current = session ?: return false
            if (current.refreshToken != expected.refreshToken || current.refreshAttemptSecret != expected.refreshAttemptSecret) return false
            session = current.copy(accessToken = refreshed.accessToken, refreshToken = refreshed.refreshToken,
                accessExpiresAtEpochMillis = refreshed.accessExpiresAtEpochMillis,
                refreshExpiresAtEpochMillis = refreshed.refreshExpiresAtEpochMillis, refreshAttemptSecret = null)
            return true
        }
        override fun updateSession(serverUrl: String, expected: StoredAuthSession, updated: StoredAuthSession): Boolean {
            if (session?.refreshToken != expected.refreshToken || session?.refreshAttemptSecret != expected.refreshAttemptSecret) return false
            session = updated
            return true
        }
        override fun clearSession(serverUrl: String, expected: StoredAuthSession): Boolean {
            if (session?.refreshToken != expected.refreshToken || session?.refreshAttemptSecret != expected.refreshAttemptSecret) return false
            if (failClear) throw AuthSessionStoreException("TEST_STORAGE_FAILED", "Synthetic clear failure")
            session = null
            return true
        }
    }

    companion object {
        private const val URL = "http://recovery.invalid"
        private val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        private val INITIAL = StoredAuthSession("fixture", "fixture@example.invalid", "Fixture", "old-access", "old-refresh", 0L, Long.MAX_VALUE)
        private fun controller(store: AuthSessionStore, api: AuthApiClient) =
            AuthController(URL, store, InMemoryResumeTokenStore(), "test", api = api)
        private fun api(handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
            AuthApiClient(URL, client = HttpClient(MockEngine(handler)) { install(ContentNegotiation) { json(ProtocolJson) } })
        private inline fun <reified T> io.ktor.client.request.HttpRequestData.decode(): T =
            ProtocolJson.decodeFromString((body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
        private fun MockRequestHandleScope.success(accessToken: String = "fresh-access", refreshToken: String = "fresh-refresh") = respond(
            """{"userId":"fixture","displayName":"Fixture","accessToken":"$accessToken","refreshToken":"$refreshToken","accessExpiresAtEpochMillis":9223372036854775807,"refreshExpiresAtEpochMillis":9223372036854775807}""", headers = JSON)
    }
}
