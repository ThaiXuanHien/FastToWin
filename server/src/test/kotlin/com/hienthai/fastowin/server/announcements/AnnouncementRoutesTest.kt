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
        val preflight = client.request("/admin/api/announcements") {
            method = io.ktor.http.HttpMethod.Options
            header(HttpHeaders.Origin, "http://localhost:8080")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        assertEquals(HttpStatusCode.NotFound, preflight.status)
        assertEquals("no-store", preflight.headers[HttpHeaders.CacheControl])
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
