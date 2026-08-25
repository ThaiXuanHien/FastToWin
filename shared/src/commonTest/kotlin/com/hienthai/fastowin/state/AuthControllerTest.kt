package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.InMemoryAuthSessionStore
import com.hienthai.fastowin.data.network.InMemoryResumeTokenStore
import com.hienthai.fastowin.data.network.StoredAuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthControllerTest {
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

    private companion object {
        const val SERVER_URL = "ws://127.0.0.1:8080/game"
    }
}
