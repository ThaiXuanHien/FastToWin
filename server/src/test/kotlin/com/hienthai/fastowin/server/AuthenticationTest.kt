package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.AuthErrorResponse
import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.AccountActionResponse
import com.hienthai.fastowin.protocol.AccountSessionsRequest
import com.hienthai.fastowin.protocol.AccountSessionsResponse
import com.hienthai.fastowin.protocol.ChangePasswordRequest
import com.hienthai.fastowin.protocol.DeleteAccountRequest
import com.hienthai.fastowin.protocol.LoginRequest
import com.hienthai.fastowin.protocol.LogoutRequest
import com.hienthai.fastowin.protocol.PasswordResetConfirmRequest
import com.hienthai.fastowin.protocol.PasswordResetRequest
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RefreshTokenRequest
import com.hienthai.fastowin.protocol.RegisterRequest
import com.hienthai.fastowin.protocol.RevokeAccountSessionRequest
import com.hienthai.fastowin.protocol.RevokeAllAccountSessionsRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthenticationTest {
    @Test
    fun `account can list and revoke only its own device sessions`() = testApplication {
        application { gameModule(environment = "dev") }
        val email = "sessions@example.com"
        val registered = client.postJson(
            "/auth/register",
            RegisterRequest(email, PASSWORD, "Session player", "android")
        ).decode<AuthSessionResponse>()
        val iosLogin = client.postJson(
            "/auth/login",
            LoginRequest(email, PASSWORD, "ios")
        ).decode<AuthSessionResponse>()

        val listed = client.postJson(
            "/auth/sessions",
            AccountSessionsRequest(registered.accessToken)
        ).decode<AccountSessionsResponse>()
        assertEquals(2, listed.sessions.size)
        val current = listed.sessions.single { it.isCurrent }
        val iosSession = listed.sessions.single { it.devicePlatform == "ios" }
        assertEquals("android", current.devicePlatform)

        val other = client.postJson(
            "/auth/register",
            RegisterRequest("other-sessions@example.com", PASSWORD, "Other", "android")
        ).decode<AuthSessionResponse>()
        val otherSessionId = client.postJson(
            "/auth/sessions",
            AccountSessionsRequest(other.accessToken)
        ).decode<AccountSessionsResponse>().sessions.single().sessionId
        val forbidden = client.postJson(
            "/auth/sessions/revoke",
            RevokeAccountSessionRequest(registered.accessToken, otherSessionId)
        )
        assertEquals(HttpStatusCode.BadRequest, forbidden.status)
        assertEquals("SESSION_NOT_FOUND", forbidden.decode<AuthErrorResponse>().code)

        val revokedIos = client.postJson(
            "/auth/sessions/revoke",
            RevokeAccountSessionRequest(registered.accessToken, iosSession.sessionId)
        )
        assertEquals(HttpStatusCode.OK, revokedIos.status)
        assertEquals(HttpStatusCode.Unauthorized, client.postJson(
            "/auth/refresh",
            RefreshTokenRequest(iosLogin.refreshToken)
        ).status)
        val afterSingleRevoke = client.postJson(
            "/auth/sessions",
            AccountSessionsRequest(registered.accessToken)
        ).decode<AccountSessionsResponse>()
        assertEquals(listOf(current.sessionId), afterSingleRevoke.sessions.map { it.sessionId })

        val secondAndroid = client.postJson(
            "/auth/login",
            LoginRequest(email, PASSWORD, "android")
        ).decode<AuthSessionResponse>()
        val revokedAll = client.postJson(
            "/auth/sessions/revoke-all",
            RevokeAllAccountSessionsRequest(registered.accessToken)
        )
        assertEquals(HttpStatusCode.OK, revokedAll.status)
        assertEquals(HttpStatusCode.Unauthorized, client.postJson(
            "/auth/refresh",
            RefreshTokenRequest(registered.refreshToken)
        ).status)
        assertEquals(HttpStatusCode.Unauthorized, client.postJson(
            "/auth/refresh",
            RefreshTokenRequest(secondAndroid.refreshToken)
        ).status)
    }

    @Test
    fun `login is rate limited by account and client address`() = testApplication {
        var now = 1_000L
        val policies = ServerRateLimitPolicies(
            loginPerIp = RateLimitPolicy(capacity = 3, refillWindowMillis = 1_000L),
            loginPerAccount = RateLimitPolicy(capacity = 2, refillWindowMillis = 1_000L)
        )
        application {
            gameModule(
                rateLimiter = InMemoryRateLimiter(nowMillis = { now }),
                rateLimitPolicies = policies
            )
        }

        repeat(2) { attempt ->
            val response = client.postJson(
                "/auth/login",
                LoginRequest(
                    email = if (attempt == 0) " Victim@Example.com " else "victim@example.com",
                    password = "wrong-password",
                    devicePlatform = "android"
                )
            )
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

        val accountLimited = client.postJson(
            "/auth/login",
            LoginRequest("VICTIM@example.com", "wrong-password", "android")
        )
        assertEquals(HttpStatusCode.TooManyRequests, accountLimited.status)
        assertEquals("RATE_LIMITED", accountLimited.decode<AuthErrorResponse>().code)
        assertEquals("1", accountLimited.headers[HttpHeaders.RetryAfter])

        val ipLimited = client.postJson(
            "/auth/login",
            LoginRequest("other@example.com", "wrong-password", "android")
        )
        assertEquals(HttpStatusCode.TooManyRequests, ipLimited.status)

        now += 500L
        val allowedAfterRefill = client.postJson(
            "/auth/login",
            LoginRequest("victim@example.com", "wrong-password", "android")
        )
        assertEquals(HttpStatusCode.Unauthorized, allowedAfterRefill.status)
    }

    @Test
    fun `register login rotate refresh token and logout`() = testApplication {
        application { gameModule() }

        val registered = client.postJson(
            "/auth/register",
            RegisterRequest(" Player@Example.COM ", PASSWORD, "Hiền", "android")
        )
        assertEquals(HttpStatusCode.Created, registered.status)
        val registeredSession = registered.decode<AuthSessionResponse>()
        assertTrue(registeredSession.userId.isNotBlank())
        assertTrue(registeredSession.accessToken.isNotBlank())
        assertTrue(registeredSession.refreshToken.isNotBlank())

        val duplicate = client.postJson(
            "/auth/register",
            RegisterRequest("player@example.com", PASSWORD, "Tên khác", "ios")
        )
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals("EMAIL_ALREADY_EXISTS", duplicate.decode<AuthErrorResponse>().code)

        val wrongPassword = client.postJson(
            "/auth/login",
            LoginRequest("player@example.com", "wrong-password", "android")
        )
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals("INVALID_CREDENTIALS", wrongPassword.decode<AuthErrorResponse>().code)

        val loggedIn = client.postJson(
            "/auth/login",
            LoginRequest("player@example.com", PASSWORD, "android")
        )
        assertEquals(HttpStatusCode.OK, loggedIn.status)
        val loginSession = loggedIn.decode<AuthSessionResponse>()
        assertEquals(registeredSession.userId, loginSession.userId)

        val refreshed = client.postJson(
            "/auth/refresh",
            RefreshTokenRequest(loginSession.refreshToken)
        )
        assertEquals(HttpStatusCode.OK, refreshed.status)
        val refreshedSession = refreshed.decode<AuthSessionResponse>()
        assertEquals(loginSession.userId, refreshedSession.userId)
        assertNotEquals(loginSession.accessToken, refreshedSession.accessToken)
        assertNotEquals(loginSession.refreshToken, refreshedSession.refreshToken)

        val reusedToken = client.postJson(
            "/auth/refresh",
            RefreshTokenRequest(loginSession.refreshToken)
        )
        assertEquals(HttpStatusCode.Unauthorized, reusedToken.status)

        val logout = client.postJson(
            "/auth/logout",
            LogoutRequest(refreshedSession.refreshToken)
        )
        assertEquals(HttpStatusCode.NoContent, logout.status)

        val afterLogout = client.postJson(
            "/auth/refresh",
            RefreshTokenRequest(refreshedSession.refreshToken)
        )
        assertEquals(HttpStatusCode.Unauthorized, afterLogout.status)
    }

    @Test
    fun `register validates email password and display name`() = testApplication {
        application { gameModule() }

        val invalidEmail = client.postJson(
            "/auth/register",
            RegisterRequest("not-an-email", PASSWORD, "Hiền")
        )
        assertEquals(HttpStatusCode.BadRequest, invalidEmail.status)
        assertEquals("INVALID_EMAIL", invalidEmail.decode<AuthErrorResponse>().code)

        val shortPassword = client.postJson(
            "/auth/register",
            RegisterRequest("player@example.com", "123", "Hiền")
        )
        assertEquals(HttpStatusCode.BadRequest, shortPassword.status)
        assertEquals("INVALID_PASSWORD", shortPassword.decode<AuthErrorResponse>().code)

        val blankName = client.postJson(
            "/auth/register",
            RegisterRequest("player@example.com", PASSWORD, "  ")
        )
        assertEquals(HttpStatusCode.BadRequest, blankName.status)
        assertEquals("INVALID_DISPLAY_NAME", blankName.decode<AuthErrorResponse>().code)
    }

    @Test
    fun `change reset and delete account revoke sessions safely`() = testApplication {
        application { gameModule(environment = "dev") }
        val email = "security@example.com"
        val registered = client.postJson(
            "/auth/register",
            RegisterRequest(email, PASSWORD, "Security player", "android")
        ).decode<AuthSessionResponse>()

        val wrongChange = client.postJson(
            "/auth/change-password",
            ChangePasswordRequest(registered.accessToken, "wrong-password", NEW_PASSWORD)
        )
        assertEquals(HttpStatusCode.BadRequest, wrongChange.status)
        assertEquals("INVALID_CURRENT_PASSWORD", wrongChange.decode<AuthErrorResponse>().code)

        val changed = client.postJson(
            "/auth/change-password",
            ChangePasswordRequest(registered.accessToken, PASSWORD, NEW_PASSWORD)
        )
        assertEquals(HttpStatusCode.OK, changed.status)
        assertTrue(changed.decode<AccountActionResponse>().message.isNotBlank())
        val revokedRefresh = client.postJson(
            "/auth/refresh",
            RefreshTokenRequest(registered.refreshToken)
        )
        assertEquals(HttpStatusCode.Unauthorized, revokedRefresh.status)

        val loggedIn = client.postJson(
            "/auth/login",
            LoginRequest(email, NEW_PASSWORD, "android")
        ).decode<AuthSessionResponse>()
        val resetRequested = client.postJson(
            "/auth/password-reset/request",
            PasswordResetRequest(email)
        ).decode<AccountActionResponse>()
        val resetToken = requireNotNull(resetRequested.devResetToken)

        val reset = client.postJson(
            "/auth/password-reset/confirm",
            PasswordResetConfirmRequest(email, resetToken, RESET_PASSWORD)
        )
        assertEquals(HttpStatusCode.OK, reset.status)
        val reusedReset = client.postJson(
            "/auth/password-reset/confirm",
            PasswordResetConfirmRequest(email, resetToken, "another-password")
        )
        assertEquals(HttpStatusCode.BadRequest, reusedReset.status)
        assertEquals("INVALID_RESET_TOKEN", reusedReset.decode<AuthErrorResponse>().code)
        val revokedByReset = client.postJson(
            "/auth/refresh",
            RefreshTokenRequest(loggedIn.refreshToken)
        )
        assertEquals(HttpStatusCode.Unauthorized, revokedByReset.status)

        val finalSession = client.postJson(
            "/auth/login",
            LoginRequest(email, RESET_PASSWORD, "android")
        ).decode<AuthSessionResponse>()
        val wrongDelete = client.postJson(
            "/auth/delete-account",
            DeleteAccountRequest(finalSession.accessToken, "wrong-password")
        )
        assertEquals(HttpStatusCode.BadRequest, wrongDelete.status)
        val deleted = client.postJson(
            "/auth/delete-account",
            DeleteAccountRequest(finalSession.accessToken, RESET_PASSWORD)
        )
        assertEquals(HttpStatusCode.OK, deleted.status)
        val afterDelete = client.postJson(
            "/auth/login",
            LoginRequest(email, RESET_PASSWORD, "android")
        )
        assertEquals(HttpStatusCode.Unauthorized, afterDelete.status)
    }

    @Test
    fun `password hashes use unique salts and verify safely`() {
        val hasher = PasswordHasher(iterations = 1_000)
        val first = hasher.hash(PASSWORD)
        val second = hasher.hash(PASSWORD)

        assertNotEquals(first, second)
        assertTrue(hasher.verify(PASSWORD, first))
        assertTrue(!hasher.verify("wrong-password", first))
        assertTrue(!hasher.verify(PASSWORD, "invalid-hash"))
    }

    private suspend inline fun <reified T> io.ktor.client.HttpClient.postJson(path: String, body: T) =
        post(path) {
            contentType(ContentType.Application.Json)
            setBody(ProtocolJson.encodeToString(body))
        }

    private suspend inline fun <reified T> HttpResponse.decode(): T =
        ProtocolJson.decodeFromString(bodyAsText())

    private companion object {
        const val PASSWORD = "strong-password-123"
        const val NEW_PASSWORD = "new-strong-password-456"
        const val RESET_PASSWORD = "reset-strong-password-789"
    }
}
