package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.AuthErrorResponse
import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.LoginRequest
import com.hienthai.fastowin.protocol.LogoutRequest
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RefreshTokenRequest
import com.hienthai.fastowin.protocol.RegisterRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
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
    }
}
