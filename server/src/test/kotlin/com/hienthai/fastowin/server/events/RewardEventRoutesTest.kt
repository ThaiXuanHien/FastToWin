package com.hienthai.fastowin.server.events

import com.hienthai.fastowin.server.gameModule
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class RewardEventRoutesTest {
    @Test fun actualAdminAndPublicRoutesEnforceCsrfRolesPrivacyAndStrictBodies() = testApplication {
        val f=RewardEventFixture(); f.initialize()
        val other=f.addOwner()
        val creator=f.admin.authenticate(f.operator.token)!!.id
        val publisher=f.admin.authenticate(f.owner.token)!!.id
        application { gameModule(adminService=f.admin,rewardEventService=f.service) }
        fun HttpRequestBuilder.admin(ctx:RewardEventAdminContext=f.owner) {
            header(HttpHeaders.Cookie,"fasttowin_admin=${ctx.token}")
            header(HttpHeaders.Origin,ctx.origin!!)
            ctx.csrfToken?.let { header("X-Admin-CSRF",it) }
        }
        suspend fun create(ctx:RewardEventAdminContext,body:String=Json.encodeToString(f.createRequest()))=client.post("/admin/api/events") {
            admin(ctx); contentType(ContentType.Application.Json); setBody(body)
        }
        for(ctx in listOf(f.support,f.owner.copy(csrfToken=null),f.owner.copy(origin="https://evil.invalid"))) {
            val response=create(ctx); assertEquals(HttpStatusCode.Forbidden,response.status)
            assertEquals("no-store",response.headers[HttpHeaders.CacheControl])
        }
        val created=create(f.operator)
        assertEquals(HttpStatusCode.OK,created.status)
        val value=Json.decodeFromString<RewardEventAdminView>(created.bodyAsText())
        assertEventIdentities(value,creator,null)
        val hidden=client.get("/api/events/${value.id}")
        assertEquals(HttpStatusCode.NotFound,hidden.status); assertEquals("no-store",hidden.headers[HttpHeaders.CacheControl])
        val preview=client.post("/admin/api/events/${value.id}/preview") { admin() }
        val token=Json.decodeFromString<RewardEventPreview>(preview.bodyAsText()).previewToken
        val scheduled=client.post("/admin/api/events/${value.id}/schedule") {
            admin(); contentType(ContentType.Application.Json); setBody(Json.encodeToString(f.mutation(value.version).copy(previewToken=token)))
        }
        assertEquals(HttpStatusCode.OK,scheduled.status)
        assertEventIdentities(Json.decodeFromString(scheduled.bodyAsText()),creator,publisher)
        val public=client.get("/api/events")
        assertEquals("no-store",public.headers[HttpHeaders.CacheControl])
        val page=Json.decodeFromString<RewardEventPublicPage>(public.bodyAsText())
        assertEquals(25,page.pageSize); assertEquals(f.now,page.serverNowEpochMillis); assertEquals(value.id,page.items.single().id)
        for(secret in listOf("actorId","creatorId","publisherId",creator,publisher,"Internal reason","requestId","previewToken")) assertFalse(secret in public.bodyAsText())
        val cancelled=client.post("/admin/api/events/${value.id}/cancel") {
            admin(other); contentType(ContentType.Application.Json); setBody(Json.encodeToString(f.mutation(2)))
        }
        assertEquals(HttpStatusCode.OK,cancelled.status)
        val detail=client.get("/admin/api/events/${value.id}") { admin() }
        assertEventIdentities(Json.decodeFromString(detail.bodyAsText()),creator,publisher)
        val publicDetail=client.get("/api/events/${value.id}").bodyAsText()
        for(secret in listOf("actorId","creatorId","publisherId",creator,publisher)) assertFalse(secret in publicDetail)
        assertEquals(HttpStatusCode.BadRequest,client.get("/api/events?pageSize=101").status)
        assertEquals(HttpStatusCode.BadRequest,create(f.owner,"{\"unexpected\":true}").status)
        assertEquals(HttpStatusCode.BadRequest,create(f.owner,"x".repeat(32_769)).status)
        assertEquals(HttpStatusCode.BadRequest,client.get("/admin/api/events?state=UNKNOWN") { admin() }.status)
        assertEquals(HttpStatusCode.Forbidden,client.get("/admin/api/events") { admin(f.support) }.status)
    }

    @Test fun absentStorageReturnsSafeUnavailableWithNoStore() = testApplication {
        application { gameModule() }
        for (path in listOf("/api/events", "/api/events/00000000-0000-0000-0000-000000000001")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertTrue(response.bodyAsText().contains("STORAGE_UNAVAILABLE"))
        }
    }
}
