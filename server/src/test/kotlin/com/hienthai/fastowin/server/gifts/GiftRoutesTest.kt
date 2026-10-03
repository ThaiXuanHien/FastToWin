package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.gameModule
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class GiftRoutesTest {
    @Test fun `api alias and legacy mailbox share authenticated entitlement and one claim receipt`() = testApplication {
        val f=MemoryGiftFixture(); val campaign=runBlocking { f.initialize(); f.publish().also { f.gifts.deliverDueBatch() } }
        application { gameModule(adminService=f.admin,giftService=f.gifts,authService=f.auth) }
        for (prefix in listOf("/api/gifts", "/gifts")) {
            val denied=client.get("$prefix/mailbox") { header(HttpHeaders.Cookie,"fasttowin_admin=${f.owner.token}") }
            assertEquals(HttpStatusCode.Unauthorized,denied.status)
            assertEquals("no-store",denied.headers[HttpHeaders.CacheControl])
            val mailbox=client.get("$prefix/mailbox?page=0&pageSize=1") { bearerAuth(f.accessToken) }
            assertEquals(HttpStatusCode.OK,mailbox.status)
            assertEquals("no-store",mailbox.headers[HttpHeaders.CacheControl])
            assertEquals(campaign.id,ProtocolJson.decodeFromString<GiftMailboxPage>(mailbox.bodyAsText()).items.single().campaignId)
            assertEquals(HttpStatusCode.BadRequest,client.get("$prefix/mailbox?page=-1") { bearerAuth(f.accessToken) }.status)
            assertEquals(HttpStatusCode.Unauthorized,client.post("$prefix/mailbox/${campaign.id}/claim").status)
        }
        val claim=client.post("/api/gifts/mailbox/${campaign.id}/claim") { bearerAuth(f.accessToken) }
        assertEquals(HttpStatusCode.OK,claim.status)
        val receipt=ProtocolJson.decodeFromString<GiftClaimResult>(claim.bodyAsText())
        val retry=client.post("/gifts/mailbox/${campaign.id}/claim") { bearerAuth(f.accessToken) }
        assertEquals(HttpStatusCode.OK,retry.status)
        assertEquals(receipt,ProtocolJson.decodeFromString<GiftClaimResult>(retry.bodyAsText()))
        for (prefix in listOf("/api/gifts", "/gifts")) {
            val all=client.post("$prefix/mailbox/claim-all") {
                bearerAuth(f.accessToken); contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(GiftClaimAllRequest(listOf(campaign.id,"00000000-0000-0000-0000-000000000000"))))
            }
            assertEquals(HttpStatusCode.OK,all.status)
            val items=ProtocolJson.decodeFromString<GiftClaimAllResult>(all.bodyAsText()).items
            assertEquals(receipt,items[0].result)
            assertEquals("NOT_FOUND",items[1].error!!.code)
            val page=client.get("$prefix/mailbox") { bearerAuth(f.accessToken) }
            assertEquals(GiftState.CLAIMED,ProtocolJson.decodeFromString<GiftMailboxPage>(page.bodyAsText()).items.single().state)
        }
    }
    @Test fun `mailbox requires a registered player bearer token`() = testApplication {
        application { gameModule() }
        assertEquals(HttpStatusCode.Unauthorized,client.get("/gifts/mailbox").status)
    }
    @Test fun `http admin checks current auth origin csrf and accepts draft put`() = testApplication {
        val f=MemoryGiftFixture(); runBlocking { f.initialize() }
        application { gameModule(adminService=f.admin,giftService=f.gifts,authService=f.auth) }
        val path="/admin/api/gift-campaigns"
        assertEquals(HttpStatusCode.Unauthorized,client.get(path) { bearerAuth(f.accessToken) }.status)
        assertEquals(HttpStatusCode.Forbidden,client.post(path) { contentType(ContentType.Application.Json); setBody(Json.encodeToString(f.request())) }.status)
        assertEquals(HttpStatusCode.Forbidden,client.post(path) {
            header(HttpHeaders.Origin,f.owner.origin!!); header(HttpHeaders.Cookie,"fasttowin_admin=${f.owner.token}")
            contentType(ContentType.Application.Json); setBody(Json.encodeToString(f.request()))
        }.status)
        val create=client.post(path) {
            header(HttpHeaders.Origin,f.owner.origin!!); header(HttpHeaders.Cookie,"fasttowin_admin=${f.owner.token}"); header("X-Admin-CSRF",f.owner.csrfToken!!)
            contentType(ContentType.Application.Json); setBody(Json.encodeToString(f.request()))
        }
        assertEquals(HttpStatusCode.OK,create.status); assertEquals("no-store",create.headers[HttpHeaders.CacheControl])
        val id=Json.decodeFromString<GiftCampaignDetail>(create.bodyAsText()).campaign.id
        val update=client.put("$path/$id") {
            header(HttpHeaders.Origin,f.owner.origin!!); header(HttpHeaders.Cookie,"fasttowin_admin=${f.owner.token}"); header("X-Admin-CSRF",f.owner.csrfToken!!)
            contentType(ContentType.Application.Json); setBody(Json.encodeToString(f.request().copy(expectedVersion=1,titleEn="Updated")))
        }
        assertEquals(HttpStatusCode.OK,update.status); assertEquals(2,Json.decodeFromString<GiftCampaignDetail>(update.bodyAsText()).campaign.version)
        assertEquals(HttpStatusCode.Forbidden,client.get("$path/$id") { header(HttpHeaders.Origin,"http://localhost:3000"); header(HttpHeaders.Cookie,"fasttowin_admin=${f.owner.token}") }.status)
        assertEquals(HttpStatusCode.BadRequest,client.post("$path/$id/preview") {
            header(HttpHeaders.Origin,f.owner.origin!!); header(HttpHeaders.Cookie,"fasttowin_admin=${f.owner.token}"); header("X-Admin-CSRF",f.owner.csrfToken!!)
            contentType(ContentType.Application.Json); setBody("{")
        }.status)
    }
    @Test fun `http mailbox claim pagination claim all and unavailable memory storage have safe responses`() = testApplication {
        val f=MemoryGiftFixture(); val campaign=runBlocking { f.initialize(); f.publish().also { f.gifts.deliverDueBatch() } }
        application { gameModule(adminService=f.admin,giftService=f.gifts,authService=f.auth) }
        val mailbox=client.get("/gifts/mailbox?page=0&pageSize=1") { bearerAuth(f.accessToken) }
        assertEquals(HttpStatusCode.OK,mailbox.status)
        val page=ProtocolJson.decodeFromString<GiftMailboxPage>(mailbox.bodyAsText())
        assertEquals(1,page.total); assertEquals(GiftState.AVAILABLE,page.items.single().state)
        assertEquals(HttpStatusCode.BadRequest,client.get("/gifts/mailbox?page=-1") { bearerAuth(f.accessToken) }.status)
        assertEquals(HttpStatusCode.Unauthorized,client.post("/gifts/mailbox/${campaign.id}/claim") { header(HttpHeaders.Cookie,"fasttowin_admin=${f.owner.token}") }.status)
        val claim=client.post("/gifts/mailbox/${campaign.id}/claim") { bearerAuth(f.accessToken) }
        assertEquals(HttpStatusCode.OK,claim.status); assertEquals(100,ProtocolJson.decodeFromString<GiftClaimResult>(claim.bodyAsText()).gold)
        val all=client.post("/gifts/mailbox/claim-all") {
            bearerAuth(f.accessToken); contentType(ContentType.Application.Json); setBody(Json.encodeToString(GiftClaimAllRequest(listOf(campaign.id))))
        }
        assertEquals(HttpStatusCode.OK,all.status); assertEquals(GiftState.CLAIMED,ProtocolJson.decodeFromString<GiftClaimAllResult>(all.bodyAsText()).items.single().result!!.gift.state)
    }
    @Test fun `registered player gets explicit unavailable in no database runtime`() = testApplication {
        val f=MemoryGiftFixture(); runBlocking { f.initialize() }
        application { gameModule(authService=f.auth) }
        assertEquals(HttpStatusCode.ServiceUnavailable,client.get("/gifts/mailbox") { bearerAuth(f.accessToken) }.status)
    }
}
