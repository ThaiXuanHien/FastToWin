package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.gameModule
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class AdminRoutesTest {
    @Test fun `gift recipient endpoint requires admin session returns public code and never caches lookup`() = testApplication {
        val f = AdminFixture()
        val login = runBlocking {
            f.owner()
            f.repository.seedUser(UserDetail("11111111-1111-1111-1111-111111111111", "Player", "player@example.com", "REGISTERED", "ACTIVE", 1, 1, 0, 0, 0, 1000, null, null, null, playerCode = "FTW123AB"))
            f.login()
        }
        application { gameModule(adminService = f.service) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/api/gift-recipient?q=FTW123AB").status)
        for (reference in listOf("ftw123ab", "PLAYER@EXAMPLE.COM")) {
            val response = client.get("/admin/api/gift-recipient") {
                parameter("q", reference); header(HttpHeaders.Cookie, "fasttowin_admin=${login.token}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertEquals("FTW123AB", Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("playerCode").jsonPrimitive.content)
        }
        val unknown = client.get("/admin/api/gift-recipient?q=FTW123") { header(HttpHeaders.Cookie, "fasttowin_admin=${login.token}") }
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/admin/api/gift-recipient?q=FTW123AB") {
            header(HttpHeaders.Cookie, "fasttowin_admin=${login.token}"); header(HttpHeaders.Origin, "https://untrusted.example")
        }.status)
    }
    @Test fun `disabled admin has no routes`() = testApplication {
        application { gameModule() }
        assertEquals(HttpStatusCode.NotFound, client.get("/admin/api/users").status)
    }
    @Test fun `player bearer cannot authorize admin and protected response is not cached`() = testApplication {
        val f = AdminFixture(); runBlocking { f.owner() }
        application { gameModule(adminService = f.service) }
        val response = client.get("/admin/api/users") { header(HttpHeaders.Authorization, "Bearer player-token") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }
    @Test fun `bounded login bodies and invalid json return safe errors and production cookie is secure`() = testApplication {
        val f=AdminFixture(); runBlocking { f.owner() }
        val service=AdminService(f.repository,AdminConfiguration("https://admin.example.com",true),f.hasher,{f.now})
        application { gameModule(adminService=service) }
        for (body in listOf("invalid", " ".repeat(8193), """{"email":"owner@example.com","password":"${"x".repeat(129)}"}""")) {
            val response=client.post("/admin/api/login") { header(HttpHeaders.Origin,"https://admin.example.com"); contentType(ContentType.Application.Json); setBody(body) }
            assertEquals(HttpStatusCode.BadRequest,response.status)
            assertEquals(setOf("code","message"),Json.parseToJsonElement(response.bodyAsText()).jsonObject.keys)
        }
        val login=client.post("/admin/api/login") {
            header(HttpHeaders.Origin,"https://admin.example.com"); contentType(ContentType.Application.Json)
            setBody("""{"email":"owner@example.com","password":"GoodPassword123!"}""")
        }
        assertEquals(HttpStatusCode.OK,login.status); assertTrue(login.headers[HttpHeaders.SetCookie]!!.contains("Secure",true))
        val cookie=login.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
        val session=client.get("/admin/api/session") { header(HttpHeaders.Cookie,cookie) }
        assertEquals(HttpStatusCode.OK,session.status)
        assertEquals(Json.parseToJsonElement(login.bodyAsText()),Json.parseToJsonElement(session.bodyAsText()))
        assertFalse(session.bodyAsText().contains(cookie.substringAfter('=')))
        assertEquals(HttpStatusCode.ServiceUnavailable,client.get("/admin/api/users") { header(HttpHeaders.Cookie,cookie) }.status)
        assertEquals(HttpStatusCode.BadRequest,client.get("/admin/api/users?page=-1") { header(HttpHeaders.Cookie,cookie) }.status)
    }
    @Test fun `allowed game origin cannot read admin session with cookie`() = testApplication {
        val f=AdminFixture(); val session=runBlocking { f.owner(); f.login() }
        application { gameModule(adminService=f.service,allowedWebOriginsValue="http://localhost:3000") }
        val response=client.get("/admin/api/session") {
            header(HttpHeaders.Origin,"http://localhost:3000"); header(HttpHeaders.Cookie,"fasttowin_admin=${session.token}")
        }
        assertEquals(HttpStatusCode.Forbidden,response.status)
        assertFalse(response.bodyAsText().contains(session.response.csrfToken))
        assertFalse(response.bodyAsText().contains("owner@example.com"))
    }
    @Test fun `generic access logs cannot leak admin email searches`() = testApplication {
        val f=AdminFixture(); val session=runBlocking { f.owner(); f.login() }
        val logger=org.slf4j.LoggerFactory.getLogger("io.ktor.test") as ch.qos.logback.classic.Logger
        val appender=ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            application { gameModule(adminService=f.service) }
            client.get("/admin/api/users?q=private%40example.com") { header(HttpHeaders.Cookie,"fasttowin_admin=${session.token}") }
            assertFalse(appender.list.any { it.formattedMessage.contains("/admin/api/users") || it.formattedMessage.contains("private") })
        } finally { logger.detachAppender(appender); appender.stop() }
    }
    @Test fun `exact origin and session bound csrf prevent logout bypass`() = testApplication {
        val f = AdminFixture(); runBlocking { f.owner() }
        application { gameModule(adminService = f.service) }
        val wrong = client.post("/admin/api/login") {
            header(HttpHeaders.Origin, "http://localhost:8080.evil.test"); contentType(ContentType.Application.Json)
            setBody("""{"email":"owner@example.com","password":"GoodPassword123!"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, wrong.status)
        assertEquals(setOf("code","message"),Json.parseToJsonElement(wrong.bodyAsText()).jsonObject.keys)
        assertEquals("no-store",wrong.headers[HttpHeaders.CacheControl])
        val login = client.post("/admin/api/login") {
            header(HttpHeaders.Origin, f.configuration.origin); contentType(ContentType.Application.Json)
            setBody("""{"email":"owner@example.com","password":"GoodPassword123!"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val cookie = login.headers[HttpHeaders.SetCookie]!!
        assertTrue(cookie.contains("HttpOnly", true)); assertTrue(cookie.contains("SameSite=Strict", true)); assertTrue(cookie.contains("Path=/admin", true))
        val sessionCookie = cookie.substringBefore(';')
        val csrf = Json.parseToJsonElement(login.bodyAsText()).jsonObject["csrfToken"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.Forbidden, client.post("/admin/api/logout") {
            header(HttpHeaders.Cookie, sessionCookie); header(HttpHeaders.Origin, f.configuration.origin)
        }.status)
        assertEquals(HttpStatusCode.OK, client.post("/admin/api/logout") {
            header(HttpHeaders.Cookie, sessionCookie); header(HttpHeaders.Origin, f.configuration.origin); header("X-Admin-CSRF", csrf)
        }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/api/session") { header(HttpHeaders.Cookie, sessionCookie) }.status)
    }
}
