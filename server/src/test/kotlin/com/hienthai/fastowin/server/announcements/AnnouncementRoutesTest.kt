package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.server.AuthenticationService
import com.hienthai.fastowin.server.AuthResult
import com.hienthai.fastowin.server.InMemoryAuthRepository
import com.hienthai.fastowin.server.InMemoryNotificationRepository
import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.gameModule
import com.hienthai.fastowin.server.admin.InMemoryAdminRepository
import com.hienthai.fastowin.server.admin.UserDetail
import com.hienthai.fastowin.server.admin.AdminService
import com.hienthai.fastowin.server.admin.AdminConfiguration
import com.hienthai.fastowin.protocol.ProtocolJson
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.server.routing.routing
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import com.hienthai.fastowin.protocol.ProtocolJson
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class AnnouncementRoutesTest {
    @Test fun `browser banner preflight permits client cache directive only from configured origin`() = testApplication {
        val origin = "http://127.0.0.1:18081"
        application { gameModule(allowedWebOriginsValue = origin) }
        val allowed = client.request("/api/announcements/banners") {
            method = io.ktor.http.HttpMethod.Options
            header(HttpHeaders.Origin, origin)
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "authorization,cache-control")
        }
        assertEquals(HttpStatusCode.OK, allowed.status)
        assertEquals(origin, allowed.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue(allowed.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty().lowercase().contains("cache-control"))
        assertEquals("no-store", allowed.headers[HttpHeaders.CacheControl])
        val denied = client.request("/api/announcements/banners") {
            method = io.ktor.http.HttpMethod.Options
            header(HttpHeaders.Origin, "https://untrusted.invalid")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "authorization,cache-control")
        }
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertEquals("no-store", denied.headers[HttpHeaders.CacheControl])
    }

    @Test fun `player inbox rejects missing authentication`() = testApplication {
        application {
            val service = AnnouncementService(InMemoryNotificationRepository().announcements, null)
            announcementModuleForTest(service)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/announcements").status)
    }

    @Test fun `admin-off preflight and get are early 404 with no-store`() = testApplication {
        application { gameModule() }
        val get = client.get("/admin/api/announcements")
        assertEquals(HttpStatusCode.NotFound, get.status)
        assertEquals("no-store", get.headers[HttpHeaders.CacheControl])
        assertEquals("nosniff", get.headers["X-Content-Type-Options"])
        val preflight = client.request("/admin/api/announcements") {
            method = io.ktor.http.HttpMethod.Options
            header(HttpHeaders.Origin, "http://localhost:8080")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertEquals(HttpStatusCode.NotFound, preflight.status)
        assertEquals("no-store", preflight.headers[HttpHeaders.CacheControl])
        assertEquals("nosniff", preflight.headers["X-Content-Type-Options"])
        val deniedCors = client.request("/api/announcements") {
            method = io.ktor.http.HttpMethod.Options
            header(HttpHeaders.Origin, "https://untrusted.invalid")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertEquals(HttpStatusCode.Forbidden, deniedCors.status)
        assertEquals("no-store", deniedCors.headers[HttpHeaders.CacheControl])
        assertEquals("nosniff", deniedCors.headers["X-Content-Type-Options"])

    }

    @Test fun `two bearer devices reload durable read and hidden banner without wallet action`() = testApplication {
        val now = 1_800_000_000_000L
        val auth = AuthenticationService(InMemoryAuthRepository(), PasswordHasher(iterations = 1_000), { now })
        val session = runBlocking { assertIs<AuthResult.Success>(auth.register("announce-route-${UUID.randomUUID()}@example.com",
            "password123", "Player", "android")).session }
        val admins = InMemoryAdminRepository()
        runBlocking { admins.seedUser(UserDetail(session.userId, "Player", "p@example.com", "REGISTERED", "ACTIVE",
            now, 1, 0, 5, 0, 1000, null, null, null)) }
        val notifications = InMemoryNotificationRepository(admins)
        val id = UUID.randomUUID().toString()
        val definition = AnnouncementRepositoryTest().fixtureDefinition(id).copy(startsAtEpochMillis = now - 1,
            endsAtEpochMillis = now + 10_000, showBanner = true)
        runBlocking {
            notifications.announcements.transaction {
                putDefinition(definition)
                putRecipient(AnnouncementRecipient(id, session.userId, storedAtEpochMillis = now))
                this.notifications.create(session.userId, listOf(definition.notification("en", now)))
            }
        }
        application {
            install(ContentNegotiation) { json(ProtocolJson) }
            routing { announcementRoutes(AnnouncementService(notifications.announcements, null, nowMillis = { now }), auth, false) }
        }
        val before = client.get("/api/announcements/banners") { header(HttpHeaders.Authorization, "Bearer ${session.accessToken}") }
        assertEquals(HttpStatusCode.OK, before.status)
        assertEquals(1, Json.parseToJsonElement(before.bodyAsText()).jsonObject["items"]!!.jsonArray.size)
        assertEquals("no-store", before.headers[HttpHeaders.CacheControl])
        assertEquals(HttpStatusCode.OK, client.post("/api/announcements/$id/read") {
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
        }.status)
        assertEquals(HttpStatusCode.OK, client.post("/api/announcements/$id/hide-banner") {
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
        }.status)
        val after = client.get("/api/announcements/banners") { header(HttpHeaders.Authorization, "Bearer ${session.accessToken}") }
        assertTrue(Json.parseToJsonElement(after.bodyAsText()).jsonObject["items"]!!.jsonArray.isEmpty())
        val detail = client.get("/api/announcements/$id") { header(HttpHeaders.Authorization, "Bearer ${session.accessToken}") }
        assertEquals("true", Json.parseToJsonElement(detail.bodyAsText()).jsonObject["notification"]!!.jsonObject["isRead"]!!.jsonPrimitive.content)
        val preference = client.get("/api/announcements/push-preferences") { header(HttpHeaders.Authorization, "Bearer ${session.accessToken}") }
        assertEquals("false", Json.parseToJsonElement(preference.bodyAsText()).jsonObject["adminEnabled"]!!.jsonPrimitive.content)
        repeat(2) {
            val saved = client.put("/api/announcements/push-preferences") {
                header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
                contentType(ContentType.Application.Json)
                setBody("""{"expectedVersion":1,"adminEnabled":true,"eventEnabled":false}""")
            }
            assertEquals(HttpStatusCode.OK, saved.status)
            assertEquals("2", Json.parseToJsonElement(saved.bodyAsText()).jsonObject["version"]!!.jsonPrimitive.content)
        }
        assertEquals("5", admins.transaction { user(session.userId)!!.gold.toString() })
    }

    @Test fun `player route pages all stored history beyond legacy websocket limit`() = testApplication {
        val now = 1_800_000_000_000L
        val auth = AuthenticationService(InMemoryAuthRepository(), PasswordHasher(iterations = 1_000), { now })
        val session = runBlocking { assertIs<AuthResult.Success>(auth.register("announce-pages-${UUID.randomUUID()}@example.com",
            "password123", "Player", "android")).session }
        val admins = InMemoryAdminRepository()
        runBlocking { admins.seedUser(UserDetail(session.userId, "Player", "p@example.com", "REGISTERED", "ACTIVE",
            now, 1, 0, 0, 0, 1000, null, null, null)) }
        val notifications = InMemoryNotificationRepository(admins)
        runBlocking { notifications.announcements.transaction {
            repeat(105) { index ->
                val id = UUID.randomUUID().toString()
                val definition = AnnouncementRepositoryTest().fixtureDefinition(id).copy(
                    startsAtEpochMillis = now - 1, endsAtEpochMillis = now + 10_000)
                putDefinition(definition)
                putRecipient(AnnouncementRecipient(id, session.userId, storedAtEpochMillis = now + index))
                this.notifications.create(session.userId, listOf(definition.notification("en", now + index)))
            }
        } }
        application {
            install(ContentNegotiation) { json(ProtocolJson) }
            routing { announcementRoutes(AnnouncementService(notifications.announcements, null, nowMillis = { now }), auth, false) }
        }
        val response = client.get("/api/announcements?page=4&pageSize=25") {
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("105", body["total"]!!.jsonPrimitive.content)
        assertEquals(5, body["items"]!!.jsonArray.size)
        val readAll = client.post("/api/announcements/read-all") { header(HttpHeaders.Authorization, "Bearer ${session.accessToken}") }
        assertEquals(HttpStatusCode.OK, readAll.status)
        assertEquals("105", Json.parseToJsonElement(readAll.bodyAsText()).jsonObject["affected"]!!.jsonPrimitive.content)
        val readPage = client.get("/api/announcements?page=4&pageSize=25") { header(HttpHeaders.Authorization, "Bearer ${session.accessToken}") }
        assertTrue(Json.parseToJsonElement(readPage.bodyAsText()).jsonObject["items"]!!.jsonArray.all {
            it.jsonObject["notification"]!!.jsonObject["isRead"]!!.jsonPrimitive.content == "true"
        })
        val dismissed = client.post("/api/announcements/dismiss-all") { header(HttpHeaders.Authorization, "Bearer ${session.accessToken}") }
        assertEquals(HttpStatusCode.OK, dismissed.status)
        assertEquals("105", Json.parseToJsonElement(dismissed.bodyAsText()).jsonObject["affected"]!!.jsonPrimitive.content)
        val history = client.get("/api/announcements?page=4&pageSize=25") { header(HttpHeaders.Authorization, "Bearer ${session.accessToken}") }
        val historyBody = Json.parseToJsonElement(history.bodyAsText()).jsonObject
        assertEquals("105", historyBody["total"]!!.jsonPrimitive.content)
        assertTrue(historyBody["items"]!!.jsonArray.all { it.jsonObject["dismissed"]!!.jsonPrimitive.content == "true" })
        val retry = client.post("/api/announcements/dismiss-all") { header(HttpHeaders.Authorization, "Bearer ${session.accessToken}") }
        assertEquals("0", Json.parseToJsonElement(retry.bodyAsText()).jsonObject["affected"]!!.jsonPrimitive.content)

    }


    @Test fun `round1 HTTP accepts maximum escaped audience and copy and rejects byte overflow`() = testApplication {
        val admins = InMemoryAdminRepository()
        val adminService = AdminService(admins, AdminConfiguration("http://localhost:8080", false), PasswordHasher(iterations = 1_000))
        val login = runBlocking {
            adminService.bootstrapOwner("owner@example.com", "GoodPassword123!")
            adminService.login("owner@example.com", "GoodPassword123!", "http://localhost:8080", "127.0.0.1")
        }
        application {
            install(ContentNegotiation) { json(ProtocolJson) }
            routing { announcementRoutes(AnnouncementService(InMemoryNotificationRepository(admins).announcements, adminService),
                AuthenticationService(InMemoryAuthRepository()), true) }
        }
        val request = AnnouncementDraftRequest("a".repeat(160), "a".repeat(160), "a".repeat(4000), "a".repeat(4000),
            com.hienthai.fastowin.protocol.AdminAnnouncementCategory.GENERAL, priority = 100,
            startsAtEpochMillis = 0, endsAtEpochMillis = 253402300799999,
            audience = AnnouncementAudience.USER_IDS, selectedUserIds = List(10000) { UUID.randomUUID().toString() },
            requestId = "a".repeat(128), reason = "a".repeat(500))
        val fullJson = Json { encodeDefaults = true }
        fun escaped(value: AnnouncementDraftRequest): String {
            val raw = fullJson.encodeToString(AnnouncementDraftRequest.serializer(), value)
            return Regex("\"[^\"]*\"").replace(raw) { match ->
                "\"" + match.value.drop(1).dropLast(1).map { "\\u%04x".format(it.code) }.joinToString("") + "\""
            }
        }
        suspend fun send(body: String, id: String? = null) = client.request("/admin/api/announcements" + (id?.let { "/$it" } ?: "")) {
            method = if (id == null) io.ktor.http.HttpMethod.Post else io.ktor.http.HttpMethod.Put
            header(HttpHeaders.Cookie, "fasttowin_admin=${login.token}")
            header(HttpHeaders.Origin, "http://localhost:8080"); header("X-Admin-CSRF", login.response.csrfToken)
            contentType(ContentType.Application.Json); setBody(body)
        }
        val maxBody = escaped(request)
        assertTrue(maxBody.length > 2_200_000)
        val created = send(maxBody.padEnd(2_300_000, ' '))
        assertEquals(HttpStatusCode.Created, created.status)
        val id = Json.parseToJsonElement(created.bodyAsText()).jsonObject["announcement"]!!.jsonObject["id"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.OK, send(escaped(request.copy(expectedVersion = 1, requestId = "b".repeat(128))), id).status)
        assertEquals(HttpStatusCode.BadRequest, send(maxBody.padEnd(2_300_001, ' ')).status)
        assertEquals(HttpStatusCode.BadRequest, send(escaped(request.copy(expectedVersion = 2, requestId = "c".repeat(128))).padEnd(2_300_001, ' '), id).status)
    }

    @Test fun `admin routes require current cookie and reject unknown JSON and invalid pages`() = testApplication {
        val admins = InMemoryAdminRepository()
        val adminService = AdminService(admins, AdminConfiguration("http://localhost:8080", false), PasswordHasher(iterations = 1_000))
        val login = runBlocking {
            adminService.bootstrapOwner("owner@example.com", "GoodPassword123!")
            adminService.login("owner@example.com", "GoodPassword123!", "http://localhost:8080", "127.0.0.1")
        }
        val notifications = InMemoryNotificationRepository(admins)
        application {
            install(ContentNegotiation) { json(ProtocolJson) }
            routing { announcementRoutes(AnnouncementService(notifications.announcements, adminService),
                AuthenticationService(InMemoryAuthRepository()), true) }
        }
        val unauthorized = client.get("/admin/api/announcements")
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
        assertEquals("no-store", unauthorized.headers[HttpHeaders.CacheControl])
        val cookie = "fasttowin_admin=${login.token}"
        val invalidPage = client.get("/admin/api/announcements?pageSize=101") { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.BadRequest, invalidPage.status)
        val unknown = client.post("/admin/api/announcements") {
            header(HttpHeaders.Cookie, cookie); header(HttpHeaders.Origin, "http://localhost:8080")
            header("X-Admin-CSRF", login.response.csrfToken)
            contentType(ContentType.Application.Json)
            setBody("""{"unknown":true}""")
        }
        assertEquals(HttpStatusCode.BadRequest, unknown.status)
        assertEquals("no-store", unknown.headers[HttpHeaders.CacheControl])
    }
}

private fun io.ktor.server.application.Application.announcementModuleForTest(service: AnnouncementService) {
    install(ContentNegotiation) { json(ProtocolJson) }
    routing { announcementRoutes(service, AuthenticationService(InMemoryAuthRepository()), adminEnabled = false) }
}
