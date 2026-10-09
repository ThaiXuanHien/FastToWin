package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.gameModule
import com.hienthai.fastowin.server.gifts.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class CompensationIncidentRoutesTest {
    @Test fun `incident CORS rejected and accepted preflight have early no store`() = testApplication {
        val f = MemoryGiftFixture(); runBlocking { f.initialize() }
        application { gameModule(adminService = f.admin, giftService = f.gifts) }
        for (path in listOf("/admin/api/incidents", "/admin/api/incidents/example/rounds")) {
            val rejected = client.get(path) { header(HttpHeaders.Origin, "https://evil.test") }
            assertEquals(HttpStatusCode.Forbidden, rejected.status)
            assertEquals("no-store", rejected.headers[HttpHeaders.CacheControl])
            val preflight = client.options(path) {
                header(HttpHeaders.Origin, f.owner.origin!!)
                header(HttpHeaders.AccessControlRequestMethod, "POST")
                header(HttpHeaders.AccessControlRequestHeaders, "Content-Type,X-Admin-CSRF")
            }
            assertEquals(HttpStatusCode.OK, preflight.status)
            assertEquals("no-store", preflight.headers[HttpHeaders.CacheControl])
        }
    }
    private suspend fun request(f: MemoryGiftFixture) = CompensationIncidentRequest("outage_1", "Sự cố", "Outage", "An toàn", "Safe", 0, 10,
        "PRIVATE_CAUSE", "PRIVATE_EVIDENCE", f.admin.session(f.owner.token).admin.id, "incident-create", "Fixture")
    private fun HttpRequestBuilder.context(f: MemoryGiftFixture) {
        header(HttpHeaders.Origin, f.owner.origin!!); header(HttpHeaders.Cookie, "$ADMIN_COOKIE=${f.owner.token}"); header(ADMIN_CSRF_HEADER, f.owner.csrfToken!!)
        contentType(ContentType.Application.Json)
    }
    @Test fun `incident http authentication strict mutation replay update and capability response`() = testApplication {
        val f = MemoryGiftFixture(); val r = runBlocking { f.initialize(); request(f) }
        application { gameModule(adminService = f.admin, giftService = f.gifts, authService = f.auth) }
        val path = "/admin/api/incidents"
        assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
        assertEquals(HttpStatusCode.Forbidden, client.post(path) { contentType(ContentType.Application.Json); setBody(Json.encodeToString(r)) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post(path) {
            header(HttpHeaders.Origin, f.owner.origin!!); header(HttpHeaders.Cookie, "$ADMIN_COOKIE=${f.owner.token}")
            contentType(ContentType.Application.Json); setBody(Json.encodeToString(r))
        }.status)
        val create = client.post(path) { context(f); setBody(Json.encodeToString(r)) }
        assertEquals(HttpStatusCode.OK, create.status); assertEquals("no-store", create.headers[HttpHeaders.CacheControl])
        val incident = Json.decodeFromString<CompensationIncident>(create.bodyAsText())
        assertEquals("OUTAGE_1", incident.incidentKey)
        assertEquals(incident, Json.decodeFromString<CompensationIncident>(client.post(path) { context(f); setBody(Json.encodeToString(r)) }.bodyAsText()))
        assertEquals(HttpStatusCode.Conflict, client.post(path) { context(f); setBody(Json.encodeToString(r.copy(titleEn = "Changed"))) }.status)
        val updated = client.put("$path/${incident.id}") { context(f); setBody(Json.encodeToString(r.copy(expectedVersion = 1, requestId = "edit", titleEn = "Edited"))) }
        assertEquals(2, Json.decodeFromString<CompensationIncident>(updated.bodyAsText()).version)
        val listed = client.get("$path?page=0&pageSize=1") { context(f) }
        assertEquals("1", Json.decodeFromString<CompensationPage<CompensationIncident>>(listed.bodyAsText()).total)
        val capability = client.get("$path/capabilities") { context(f) }
        assertEquals("no-store", capability.headers[HttpHeaders.CacheControl])
        val sources = Json.decodeFromString<CompensationCapabilities>(capability.bodyAsText()).sources
        assertEquals(listOf(CompensationAudience.ALL_REGISTERED, CompensationAudience.USER_IDS), sources.filter { it.available }.map { it.source })
        assertFalse(sources.single { it.source == CompensationAudience.ACTIVITY }.available)
        assertEquals(HttpStatusCode.BadRequest, client.get("$path?pageSize=101") { context(f) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("$path/bad-uuid") { context(f) }.status)
        for (body in listOf("{", Json.encodeToString(r).dropLast(1) + ",\"source_type\":\"COMPENSATION\"}", " ".repeat(524289))) {
            assertEquals(HttpStatusCode.BadRequest, client.post(path) { context(f); setBody(body) }.status)
        }
    }
    @Test fun `admin disabled excludes compensation routes even explicit service injection`() = testApplication {
        val f = MemoryGiftFixture(); runBlocking { f.initialize() }
        application { gameModule(giftService = f.gifts, compensationIncidentService = CompensationIncidentService(f.gifts.repository, f.admin)) }
        for (path in listOf("/admin/api/incidents", "/admin/api/incidents/capabilities")) {
            assertEquals(HttpStatusCode.NotFound, client.get(path).status)
            assertEquals(HttpStatusCode.NotFound, client.post(path).status)
        }
    }
    @Test fun `enabled no database incidents return safe unavailable and no store`() = testApplication {
        val f = MemoryGiftFixture(); runBlocking { f.initialize() }
        application { gameModule(adminService = f.admin) }
        val response = client.get("/admin/api/incidents") { context(f) }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status); assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("STORAGE_UNAVAILABLE", Json.decodeFromString<AdminError>(response.bodyAsText()).code)
        assertFalse(response.bodyAsText().contains("SQLException"))
    }
}
