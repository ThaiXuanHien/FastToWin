package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.InMemoryAuthSessionStore
import com.hienthai.fastowin.data.network.InMemoryResumeTokenStore
import com.hienthai.fastowin.data.network.StoredAuthSession
import com.hienthai.fastowin.data.network.AuthApiClient
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.ProtocolJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthControllerTest {
    @Test fun supportExemptionPermitsPlayingWhileEmailRemainsUnverified() {
        val account = StoredAuthSession("supported", "supported@example.test", "Supported", "access", "refresh", Long.MAX_VALUE, Long.MAX_VALUE,
            emailVerified = false, supportEmailVerificationExempt = true)
        val store = InMemoryAuthSessionStore().apply { save(SERVER_URL, account) }
        val controller = AuthController(SERVER_URL, store, InMemoryResumeTokenStore(), "test")
        try {
            assertEquals(AuthStage.PLAYING, controller.state.value.stage)
            assertFalse(controller.state.value.session!!.emailVerified)
        } finally { controller.close() }
    }
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialGuestSession_startsDirectlyInPlayingStage() {
        val controller = AuthController(
            serverUrl = SERVER_URL,
            store = InMemoryAuthSessionStore(),
            resumeTokenStore = InMemoryResumeTokenStore(),
            devicePlatform = "test",
            initialGuestSession = true
        )

        try {
            assertEquals(AuthStage.PLAYING, controller.state.value.stage)
            assertTrue(controller.state.value.isGuest)
        } finally {
            controller.close()
        }
    }

    @Test
    fun storedAccount_takesPrecedenceOverRestoredGuestFlag() {
        val account = StoredAuthSession(
            userId = "user-1",
            email = "hien@example.com",
            displayName = "Hiền",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            accessExpiresAtEpochMillis = Long.MAX_VALUE,
            refreshExpiresAtEpochMillis = Long.MAX_VALUE
        )
        val store = InMemoryAuthSessionStore().apply { save(SERVER_URL, account) }
        val controller = AuthController(
            serverUrl = SERVER_URL,
            store = store,
            resumeTokenStore = InMemoryResumeTokenStore(),
            devicePlatform = "test",
            initialGuestSession = true
        )

        try {
            assertEquals(AuthStage.PLAYING, controller.state.value.stage)
            assertFalse(controller.state.value.isGuest)
            assertEquals(account, controller.state.value.session)
        } finally {
            controller.close()
        }
    }

    @Test
    fun unverifiedStoredAccount_startsInEmailVerificationStage() {
        val account = storedSession("unverified-refresh").copy(emailVerified = false)
        val store = InMemoryAuthSessionStore().apply { save(SERVER_URL, account) }
        val controller = AuthController(
            serverUrl = SERVER_URL,
            store = store,
            resumeTokenStore = InMemoryResumeTokenStore(),
            devicePlatform = "test"
        )

        try {
            assertEquals(AuthStage.VERIFY_EMAIL, controller.state.value.stage)
            assertEquals(account, controller.state.value.session)
        } finally {
            controller.close()
        }
    }

    @Test
    fun refreshPersistsCurrentVerificationAndExemptionAndUpdatesTheAccountStage() = runTest(mainDispatcher) {
        data class Flags(val verified: Boolean, val exempt: Boolean)
        for ((initial, refreshed) in listOf(
            Flags(false, false) to Flags(false, true),
            Flags(false, true) to Flags(false, false),
            Flags(false, false) to Flags(true, false),
            Flags(true, false) to Flags(false, false),
            Flags(false, true) to Flags(true, false)
        )) {
            val account = storedSession("metadata-refresh").copy(
                emailVerified = initial.verified, supportEmailVerificationExempt = initial.exempt
            )
            val store = InMemoryAuthSessionStore().apply { save(SERVER_URL, account) }
            val response = AuthSessionResponse(account.userId, accessToken = "fresh-access", refreshToken = "fresh-refresh",
                accessExpiresAtEpochMillis = Long.MAX_VALUE, refreshExpiresAtEpochMillis = Long.MAX_VALUE,
                emailVerified = refreshed.verified, supportEmailVerificationExempt = refreshed.exempt)
            val client = HttpClient(MockEngine {
                respond(ProtocolJson.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) { install(ContentNegotiation) { json(ProtocolJson) } }
            val controller = AuthController(SERVER_URL, store, InMemoryResumeTokenStore(), "test",
                api = AuthApiClient(SERVER_URL, client = client))
            try {
                assertEquals("fresh-access", controller.validAccessToken(true))
                assertEquals(refreshed.verified, store.load(SERVER_URL)?.emailVerified)
                assertEquals(refreshed.exempt, store.load(SERVER_URL)?.supportEmailVerificationExempt)
                assertEquals(store.load(SERVER_URL), controller.state.value.session)
                assertEquals(if (refreshed.verified || refreshed.exempt) AuthStage.PLAYING else AuthStage.VERIFY_EMAIL,
                    controller.state.value.stage)
            } finally { controller.close() }
        }
    }

    @Test
    fun refreshDoesNotNavigateAwayFromAnUnrelatedPasswordResetStage() = runTest(mainDispatcher) {
        val account = storedSession("reset-metadata").copy(emailVerified = false)
        val store = InMemoryAuthSessionStore().apply { save(SERVER_URL, account) }
        val response = AuthSessionResponse(account.userId, accessToken = "fresh-access", refreshToken = "fresh-refresh",
            accessExpiresAtEpochMillis = Long.MAX_VALUE, refreshExpiresAtEpochMillis = Long.MAX_VALUE,
            emailVerified = false, supportEmailVerificationExempt = true)
        val client = HttpClient(MockEngine {
            respond(ProtocolJson.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val controller = AuthController(SERVER_URL, store, InMemoryResumeTokenStore(), "test",
            api = AuthApiClient(SERVER_URL, client = client))
        try {
            controller.openPasswordReset()
            assertEquals("fresh-access", controller.validAccessToken(true))
            assertTrue(controller.state.value.session!!.supportEmailVerificationExempt)
            assertEquals(AuthStage.RESET_PASSWORD, controller.state.value.stage)
        } finally { controller.close() }
    }

    @Test
    fun expiredOldTab_doesNotClearNewerSessionInSharedStore() {
        val oldSession = storedSession("old-refresh")
        val newSession = storedSession("new-refresh")
        val store = InMemoryAuthSessionStore().apply { save(SERVER_URL, oldSession) }
        val controller = AuthController(
            serverUrl = SERVER_URL,
            store = store,
            resumeTokenStore = InMemoryResumeTokenStore(),
            devicePlatform = "web"
        )

        try {
            store.save(SERVER_URL, newSession)
            controller.expireSession("Đã đăng nhập ở thiết bị khác.")

            assertEquals(newSession, store.load(SERVER_URL))
            assertEquals(AuthStage.LOGIN, controller.state.value.stage)
            assertEquals("Đã đăng nhập ở thiết bị khác.", controller.state.value.error)
        } finally {
            controller.close()
        }
    }

    @Test
    fun logout_clearsSessionAndNavigatesImmediatelyToLogin() {
        val session = storedSession("logout-refresh")
        val store = InMemoryAuthSessionStore().apply { save(SERVER_URL, session) }
        val controller = AuthController(
            serverUrl = SERVER_URL,
            store = store,
            resumeTokenStore = InMemoryResumeTokenStore(),
            devicePlatform = "test"
        )

        try {
            controller.logout()

            assertEquals(AuthStage.LOGIN, controller.state.value.stage)
            assertEquals(null, controller.state.value.session)
            assertEquals(null, store.load(SERVER_URL))
        } finally {
            controller.close()
        }
    }

    @Test
    fun updateLanguageLocalizesSessionExpiryWithoutRecreatingController() {
        val session = storedSession("language-refresh")
        val store = InMemoryAuthSessionStore().apply { save(SERVER_URL, session) }
        val controller = AuthController(
            serverUrl = SERVER_URL,
            store = store,
            resumeTokenStore = InMemoryResumeTokenStore(),
            devicePlatform = "test"
        )

        try {
            controller.updateLanguage(AppLanguage.ENGLISH)
            controller.expireSession()

            assertEquals(AuthStage.LOGIN, controller.state.value.stage)
            assertEquals(
                "Your session has expired. Please sign in again.",
                controller.state.value.error
            )
        } finally {
            controller.close()
        }
    }

    private fun storedSession(refreshToken: String) = StoredAuthSession(
        userId = "user-1",
        email = "hien@example.com",
        displayName = "Hiền",
        accessToken = "access-$refreshToken",
        refreshToken = refreshToken,
        accessExpiresAtEpochMillis = Long.MAX_VALUE,
        refreshExpiresAtEpochMillis = Long.MAX_VALUE
    )

    private companion object {
        const val SERVER_URL = "ws://127.0.0.1:8080/game"
    }
}
