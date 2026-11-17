package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.AuthApiClient
import com.hienthai.fastowin.data.network.InMemoryAuthSessionStore
import com.hienthai.fastowin.data.network.InMemoryResumeTokenStore
import com.hienthai.fastowin.protocol.AccountActionResponse
import com.hienthai.fastowin.protocol.AuthErrorResponse
import com.hienthai.fastowin.protocol.HumanVerificationAction
import com.hienthai.fastowin.protocol.HumanVerificationConfigResponse
import com.hienthai.fastowin.protocol.PasswordResetRequest
import com.hienthai.fastowin.protocol.ProtocolJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthHumanVerificationTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /** Fake server: [configs] answers successive config reads; reset requests are recorded. */
    private class FakeServer(
        private val configs: List<HumanVerificationConfigResponse>,
        private val requireTokenOnFirstReset: Boolean = false
    ) {
        var configReads = 0
        val resetRequests = mutableListOf<PasswordResetRequest>()
        val client = HttpClient(MockEngine { request ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/auth/human-verification" -> {
                    val config = configs[minOf(configReads, configs.lastIndex)]
                    configReads++
                    respond(ProtocolJson.encodeToString(config), headers = json)
                }
                request.url.encodedPath == "/auth/password-reset/request" -> {
                    val body = when (val content = request.body) {
                        is io.ktor.http.content.TextContent -> content.text
                        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
                        else -> error("Unexpected body ${content::class}")
                    }
                    val parsed = ProtocolJson.decodeFromString<PasswordResetRequest>(body)
                    resetRequests += parsed
                    if (requireTokenOnFirstReset && resetRequests.size == 1) {
                        respond(
                            ProtocolJson.encodeToString(AuthErrorResponse("HUMAN_VERIFICATION_REQUIRED", "Verify")),
                            HttpStatusCode.BadRequest, json
                        )
                    } else {
                        respond(ProtocolJson.encodeToString(AccountActionResponse("sent", messageKey = "PasswordResetSent")), headers = json)
                    }
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
    }

    private fun controller(server: FakeServer) = AuthController(
        SERVER_URL, InMemoryAuthSessionStore(), InMemoryResumeTokenStore(), "test",
        api = AuthApiClient(SERVER_URL, client = server.client)
    )

    @Test
    fun enabledTurnstileAsksForATokenBeforeSendingTheReset() = runTest(mainDispatcher) {
        val server = FakeServer(listOf(HumanVerificationConfigResponse(enabled = true, siteKey = "site-key")))
        val controller = controller(server)
        try {
            controller.requestPasswordReset("player@example.test")
            val challenge = controller.humanVerification.first { it != null }!!
            assertEquals(HumanVerificationAction.PASSWORD_RESET, challenge.action)
            assertEquals("site-key", challenge.siteKey)
            assertEquals("http://127.0.0.1:8080/auth/human-verification/challenge", challenge.challengePageUrl)
            assertTrue(server.resetRequests.isEmpty())
            assertTrue(controller.state.value.isLoading)

            controller.completeHumanVerification("solved-token")
            controller.state.first { !it.isLoading }
            assertEquals("solved-token", server.resetRequests.single().humanVerificationToken)
            assertNull(controller.humanVerification.value)
            assertFalse(controller.state.value.isLoading)
            assertNull(controller.state.value.error)
        } finally { controller.close() }
    }

    @Test
    fun closingTheChallengeLeavesTheFormWithoutAnError() = runTest(mainDispatcher) {
        val server = FakeServer(listOf(HumanVerificationConfigResponse(enabled = true, siteKey = "site-key")))
        val controller = controller(server)
        try {
            controller.requestPasswordReset("player@example.test")
            controller.humanVerification.first { it != null }
            controller.cancelHumanVerification()
            controller.state.first { !it.isLoading }
            assertNull(controller.humanVerification.value)
            assertTrue(server.resetRequests.isEmpty())
            assertFalse(controller.state.value.isLoading)
            assertNull(controller.state.value.error)
        } finally { controller.close() }
    }

    @Test
    fun disabledTurnstileSendsWithoutAChallenge() = runTest(mainDispatcher) {
        val server = FakeServer(listOf(HumanVerificationConfigResponse()))
        val controller = controller(server)
        try {
            controller.requestPasswordReset("player@example.test")
            controller.state.first { !it.isLoading }
            assertNull(controller.humanVerification.value)
            assertNull(server.resetRequests.single().humanVerificationToken)
            assertEquals("player@example.test", controller.state.value.passwordResetEmail)
        } finally { controller.close() }
    }

    @Test
    fun aStaleDisabledConfigIsRepairedByOneChallengedRetry() = runTest(mainDispatcher) {
        val server = FakeServer(
            configs = listOf(HumanVerificationConfigResponse(), HumanVerificationConfigResponse(enabled = true, siteKey = "site-key")),
            requireTokenOnFirstReset = true
        )
        val controller = controller(server)
        try {
            controller.requestPasswordReset("player@example.test")
            controller.humanVerification.first { it != null }
            assertEquals(2, server.configReads)
            controller.completeHumanVerification("retry-token")
            controller.state.first { !it.isLoading }
            assertEquals(listOf(null, "retry-token"), server.resetRequests.map { it.humanVerificationToken })
            assertNull(controller.state.value.error)
        } finally { controller.close() }
    }

    private companion object {
        const val SERVER_URL = "ws://127.0.0.1:8080/game"
    }
}
