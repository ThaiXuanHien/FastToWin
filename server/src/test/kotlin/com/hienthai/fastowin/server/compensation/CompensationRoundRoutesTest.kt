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

class CompensationRoundRoutesTest {
    private suspend fun incident(f: MemoryGiftFixture) = CompensationIncidentService(f.gifts.repository, f.admin, { f.now }).create(f.owner,
        CompensationIncidentRequest("OUTAGE", "Sự cố", "Outage", "An toàn", "Safe", 0, 10, "PRIVATE", "PRIVATE",
            f.admin.session(f.owner.token).admin.id, "incident", "Fixture"))
    private fun request(f: MemoryGiftFixture) = CompensationRoundRequest(1, "Ảnh hưởng", "Affected", 100, 30, f.now, "round", "Fixture", expectedNextRoundNumber = 1)
    private fun HttpRequestBuilder.context(f: MemoryGiftFixture, ctx: GiftAdminContext = f.owner) {
        header(HttpHeaders.Origin, ctx.origin!!); header(HttpHeaders.Cookie, "$ADMIN_COOKIE=${ctx.token}"); header(ADMIN_CSRF_HEADER, ctx.csrfToken!!)
        contentType(ContentType.Application.Json)
    }
    @Test fun `typed round http create edit preview publish receipt retry cancel and supplemental history`() = testApplication {
        val f = MemoryGiftFixture(); val i = runBlocking { f.initialize(); incident(f) }
        application { gameModule(adminService = f.admin, giftService = f.gifts, authService = f.auth) }
        val path = "/admin/api/incidents/${i.id}/rounds"
        val r = request(f); val create = client.post(path) { context(f); setBody(Json.encodeToString(r)) }
        assertEquals(HttpStatusCode.OK, create.status); assertEquals("no-store", create.headers[HttpHeaders.CacheControl]); assertEquals("nosniff", create.headers["X-Content-Type-Options"])
        val c = Json.decodeFromString<CompensationRoundDetail>(create.bodyAsText()); assertEquals(i.incidentKey, c.incidentKey); assertEquals("0", c.report.eligible)
        assertEquals(c, Json.decodeFromString<CompensationRoundDetail>(client.post(path) { context(f); setBody(Json.encodeToString(r)) }.bodyAsText()))
        val updated = client.put("$path/${c.campaign.id}") { context(f); setBody(Json.encodeToString(r.copy(expectedNextRoundNumber = null, expectedVersion = 1, requestId = "edit", gold = 101))) }
        assertEquals(2, Json.decodeFromString<CompensationRoundDetail>(updated.bodyAsText()).campaign.version)
        val preview = client.post("$path/${c.campaign.id}/preview") { context(f); setBody(Json.encodeToString(CompensationRoundPreviewRequest(1, 2, "preview", "Review"))) }
        val p = Json.decodeFromString<CompensationPreview>(preview.bodyAsText()); assertEquals("1", p.recipientCount); assertEquals("101", p.totalGold); assertEquals("0", p.previousClaimedUserCount)
        val publishRequest = CompensationRoundPublishRequest(1, 2, p.previewToken, "publish", "Approve")
        val published = client.post("$path/${c.campaign.id}/publish") { context(f); setBody(Json.encodeToString(publishRequest)) }
        val result = Json.decodeFromString<CompensationRoundDetail>(published.bodyAsText()); assertEquals(CampaignState.PUBLISHED, result.campaign.state); assertEquals("1", result.report.pending)
        val cancel = client.post("$path/${c.campaign.id}/cancel") { context(f); setBody(Json.encodeToString(CompensationRoundCancelRequest(1, 3, "cancel", "Stop"))) }
        assertEquals("1", Json.decodeFromString<CompensationRoundDetail>(cancel.bodyAsText()).report.cancelled)
        assertEquals(result, Json.decodeFromString<CompensationRoundDetail>(client.post("$path/${c.campaign.id}/publish") { context(f); setBody(Json.encodeToString(publishRequest)) }.bodyAsText()))
        assertEquals(HttpStatusCode.Conflict, client.post(path) { context(f); setBody(Json.encodeToString(r.copy(requestId = "duplicate"))) }.status)
        val second = client.post(path) { context(f); setBody(Json.encodeToString(r.copy(requestId = "round2", expectedNextRoundNumber = 2, supplementalReason = "Additional"))) }
        assertEquals(2, Json.decodeFromString<CompensationRoundDetail>(second.bodyAsText()).round.roundNumber)
        val history = client.get("$path?pageSize=1") { context(f) }
        val page = Json.decodeFromString<CompensationPage<CompensationRoundDetail>>(history.bodyAsText()); assertEquals("2", page.total); assertEquals(2, page.items.single().round.roundNumber)
    }
    @Test fun `round routes current roles csrf origin strict bodies source and version guards`() = testApplication {
        val f = MemoryGiftFixture(); val i = runBlocking { f.initialize(); incident(f) }
        application { gameModule(adminService = f.admin, giftService = f.gifts) }
        val path = "/admin/api/incidents/${i.id}/rounds"; val r = request(f)
        assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
        assertEquals(HttpStatusCode.Forbidden, client.post(path) { contentType(ContentType.Application.Json); setBody(Json.encodeToString(r)) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post(path) { context(f); headers.remove(ADMIN_CSRF_HEADER); setBody(Json.encodeToString(r)) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get(path) { context(f); headers[HttpHeaders.Origin] = "https://evil.test" }.status)
        for (body in listOf("{", Json.encodeToString(r).dropLast(1) + ",\"compensation\":{}}", " ".repeat(524289)))
            assertEquals(HttpStatusCode.BadRequest, client.post(path) { context(f); setBody(body) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post(path) { context(f); contentType(ContentType.Text.Plain); setBody(Json.encodeToString(r)) }.status)
        val activity = client.post(path) { context(f); setBody(Json.encodeToString(r.copy(audience = CompensationAudience.ACTIVITY))) }
        assertEquals(HttpStatusCode.BadRequest, activity.status); assertEquals("ACTIVITY_SOURCE_UNAVAILABLE", Json.decodeFromString<AdminError>(activity.bodyAsText()).code)
        assertEquals(HttpStatusCode.BadRequest, client.post(path) { context(f); setBody(Json.encodeToString(r.copy(incidentVersion = 0))) }.status)
        assertEquals(HttpStatusCode.Conflict, client.post(path) { context(f); setBody(Json.encodeToString(r.copy(incidentVersion = 2))) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("$path?pageSize=101") { context(f) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("$path/bad-uuid") { context(f) }.status)
        val c = Json.decodeFromString<CompensationRoundDetail>(client.post(path) { context(f); setBody(Json.encodeToString(r)) }.bodyAsText())
        val p = Json.decodeFromString<CompensationPreview>(client.post("$path/${c.campaign.id}/preview") { context(f); setBody(Json.encodeToString(CompensationRoundPreviewRequest(1, 1, "preview", "Review"))) }.bodyAsText())
        val contexts = runBlocking { listOf(AdminRole.OPERATOR, AdminRole.SUPPORT).associateWith { role ->
            val account = f.admin.createAccount(f.owner.token, f.owner.csrfToken, f.owner.origin, CreateAdminRequest("$role@example.com", "GoodPassword123!", role, "Fixture", "$role"))
            val login = f.admin.login(account.email, "GoodPassword123!", f.owner.origin, "127.0.0.1"); GiftAdminContext(login.token, login.response.csrfToken, f.owner.origin)
        } }
        for (ctx in contexts.values) {
            assertEquals(HttpStatusCode.OK, client.get("$path/${c.campaign.id}") { context(f, ctx) }.status)
            assertEquals(HttpStatusCode.Forbidden, client.post("$path/${c.campaign.id}/publish") { context(f, ctx); setBody(Json.encodeToString(CompensationRoundPublishRequest(1, 1, p.previewToken, "pub", "Approve"))) }.status)
            assertEquals(HttpStatusCode.Forbidden, client.post("$path/${c.campaign.id}/cancel") { context(f, ctx); setBody(Json.encodeToString(CompensationRoundCancelRequest(1, 1, "cancel", "Stop"))) }.status)
        }
        assertEquals(HttpStatusCode.Forbidden, client.post("$path/${c.campaign.id}/preview") { context(f, contexts.getValue(AdminRole.SUPPORT)); setBody(Json.encodeToString(CompensationRoundPreviewRequest(1, 1, "review", "Review"))) }.status)
        assertEquals(HttpStatusCode.OK, client.post("$path/${c.campaign.id}/preview") { context(f, contexts.getValue(AdminRole.OPERATOR)); setBody(Json.encodeToString(CompensationRoundPreviewRequest(1, 1, "review", "Review"))) }.status)
    }
    @Test fun `disabled round injection stays absent and no database is safe unavailable`() = testApplication {
        val f = MemoryGiftFixture(); runBlocking { f.initialize() }
        application { gameModule(compensationRoundService = CompensationRoundService(f.gifts.repository, f.admin)) }
        for (path in listOf("/admin/api/incidents/00000000-0000-0000-0000-000000000001/rounds", "/admin/api/incidents/00000000-0000-0000-0000-000000000001/rounds/00000000-0000-0000-0000-000000000002/preview")) {
            assertEquals(HttpStatusCode.NotFound, client.get(path).status); assertEquals(HttpStatusCode.NotFound, client.post(path).status)
        }
    }
    @Test fun `enabled no database round reads mutations give safe 503`() = testApplication {
        val f = MemoryGiftFixture(); runBlocking { f.initialize() }
        application { gameModule(adminService = f.admin) }
        val path = "/admin/api/incidents/00000000-0000-0000-0000-000000000001/rounds"
        val read = client.get(path) { context(f) }; val mutate = client.post(path) { context(f); setBody(Json.encodeToString(request(f))) }
        for (response in listOf(read, mutate)) {
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status); assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertEquals("STORAGE_UNAVAILABLE", Json.decodeFromString<AdminError>(response.bodyAsText()).code); assertFalse(response.bodyAsText().contains("SQLException"))
        }
    }
}
