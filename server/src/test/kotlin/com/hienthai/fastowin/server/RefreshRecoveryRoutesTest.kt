package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.Base64
import kotlin.test.*

class RefreshRecoveryRoutesTest {
    @Test fun `native route validates before rotation replays and logs out pending generation`() = testApplication {
        application { gameModule(environment = "dev") }
        val registered = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(ProtocolJson.encodeToString(RegisterRequest("route-recovery@example.com", "password123", "Recovery", "android")))
        }
        assertEquals(HttpStatusCode.Created, registered.status)
        val initial = ProtocolJson.decodeFromString<AuthSessionResponse>(registered.bodyAsText())
        suspend fun refresh(secret: String?) = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(ProtocolJson.encodeToString(RefreshTokenRequest(initial.refreshToken, secret)))
        }
        assertEquals(HttpStatusCode.BadRequest, refresh("invalid").status)
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 3 })
        val first = refresh(secret)
        assertEquals(HttpStatusCode.OK, first.status)
        assertNull(first.headers[HttpHeaders.SetCookie])
        val response = first.bodyAsText()
        assertEquals(response, refresh(secret).bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, refresh(null).status)
        val logout = client.post("/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(ProtocolJson.encodeToString(LogoutRequest(initial.refreshToken, secret)))
        }
        assertEquals(HttpStatusCode.NoContent, logout.status)
        assertEquals(HttpStatusCode.Unauthorized, refresh(secret).status)
        val successor = ProtocolJson.decodeFromString<AuthSessionResponse>(response)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(ProtocolJson.encodeToString(RefreshTokenRequest(successor.refreshToken)))
        }.status)
    }

    @Test fun `web ignores attempt secret and keeps cookie csrf and legacy rotation`() = testApplication {
        application { gameModule(environment = "prod", allowedWebOriginsValue = "https://play.fasttowin.vn") }
        suspend fun post(path: String, body: String, cookie: String? = null, csrf: Boolean = true) = client.post(path) {
            header(HttpHeaders.Origin, "https://play.fasttowin.vn")
            header("X-FastToWin-Web-Session", "1")
            if (csrf) header("X-FastToWin-CSRF", "1")
            cookie?.let { header(HttpHeaders.Cookie, it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val registered = post("/auth/register", ProtocolJson.encodeToString(RegisterRequest("web-recovery@example.com", "password123", "Recovery", "web")))
        assertEquals(HttpStatusCode.Created, registered.status)
        val cookie = parseServerSetCookieHeader(assertNotNull(registered.headers[HttpHeaders.SetCookie]))
        val cookieHeader = "${cookie.name}=${cookie.value}"
        val body = ProtocolJson.encodeToString(RefreshTokenRequest("", "invalid"))
        assertEquals(HttpStatusCode.Forbidden, post("/auth/refresh", body, cookieHeader, csrf = false).status)
        val first = post("/auth/refresh", body, cookieHeader)
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals("", ProtocolJson.decodeFromString<AuthSessionResponse>(first.bodyAsText()).refreshToken)
        assertEquals(HttpStatusCode.Unauthorized, post("/auth/refresh", body, cookieHeader).status)
        val rotated = parseServerSetCookieHeader(assertNotNull(first.headers[HttpHeaders.SetCookie]))
        assertTrue(rotated.httpOnly)
        assertTrue(rotated.secure)
        val logout = post("/auth/logout", ProtocolJson.encodeToString(LogoutRequest("", "invalid")), "${rotated.name}=${rotated.value}")
        assertEquals(HttpStatusCode.NoContent, logout.status)
        assertEquals(0, parseServerSetCookieHeader(assertNotNull(logout.headers[HttpHeaders.SetCookie])).maxAge)
        assertEquals(HttpStatusCode.Unauthorized, post("/auth/refresh", body, "${rotated.name}=${rotated.value}").status)
    }
}
