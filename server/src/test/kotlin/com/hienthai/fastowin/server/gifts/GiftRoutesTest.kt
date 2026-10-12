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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class GiftRoutesTest {
    @Test fun `preview HTTP projection preserves maximum totals without changing domain JSON`() {
        val preview = GiftPreview("campaign", 1, Long.MAX_VALUE, 0, emptyList(),
            9_007_199_254_740_993L, Long.MAX_VALUE, "token")
        val wire = Json.parseToJsonElement(Json.encodeToString(preview.adminResponse())).jsonObject
        assertEquals("9007199254740993", wire.getValue("totalGold").jsonPrimitive.content)
        assertEquals(Long.MAX_VALUE.toString(), wire.getValue("totalXp").jsonPrimitive.content)
        for (key in listOf("recipientCount", "totalGold", "totalXp", "totalGems")) assertTrue(wire.getValue(key).jsonPrimitive.isString)
        val persisted = Json.parseToJsonElement(Json.encodeToString(preview)).jsonObject
        assertFalse(persisted.getValue("totalGold").jsonPrimitive.isString)
        assertEquals(preview, Json.decodeFromString<GiftPreview>(persisted.toString()))
    }
    @Test fun `admin aggregates use exact decimal strings on every response`() = testApplication {
        val f = MemoryGiftFixture()
        val large = 9_007_199_254_740_993L
        val wrapped = object : GiftRepository {
            override suspend fun <T> transaction(admin: Boolean, block: GiftTransaction.() -> T): T =
                f.gifts.repository.transaction(admin) {
                    val original = this
                    block(object : GiftTransaction by original {
                        override fun campaigns(page: com.hienthai.fastowin.server.admin.PageRequest) = original.campaigns(page).copy(total = large)
                        override fun counters(campaign: GiftCampaign, now: Long) = GiftCounters(
                            eligible = large, claimedGold = large, claimedXp = Long.MAX_VALUE, claimedGems = large
                        )
                    })
                }
        }
        val gifts = GiftService(wrapped, f.admin, { f.now })
        runBlocking { f.initialize() }
        application { gameModule(adminService = f.admin, giftService = gifts, authService = f.auth) }
        suspend fun mutate(path: String, body: String, put: Boolean = false): String {
            val configure: HttpRequestBuilder.() -> Unit = {
                header(HttpHeaders.Origin, f.owner.origin!!)
                header(HttpHeaders.Cookie, "fasttowin_admin=${f.owner.token}")
                header("X-Admin-CSRF", f.owner.csrfToken!!)
                contentType(ContentType.Application.Json); setBody(body)
            }
            val response = if (put) client.put(path, configure) else client.post(path, configure)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            return response.bodyAsText()
        }
        fun assertCounters(body: String) {
            val counters = Json.parseToJsonElement(body).jsonObject.getValue("counters").jsonObject
            assertTrue(counters.values.all { it.jsonPrimitive.isString }, body)
            assertEquals(large.toString(), counters.getValue("claimedGold").jsonPrimitive.content)
            assertEquals(Long.MAX_VALUE.toString(), counters.getValue("claimedXp").jsonPrimitive.content)
            assertEquals(large.toString(), counters.getValue("claimedGems").jsonPrimitive.content)
        }
        val base = "/admin/api/gift-campaigns"
        val created = mutate(base, Json.encodeToString(f.request().copy(gems = 7)))
        val id = Json.parseToJsonElement(created).jsonObject.getValue("campaign").jsonObject.getValue("id").jsonPrimitive.content
        assertCounters(created)
        val updated = mutate("$base/$id", Json.encodeToString(f.request().copy(expectedVersion = 1, gems = 7)), true)
        assertCounters(updated)
        val preview = Json.parseToJsonElement(mutate("$base/$id/preview", "{\"expectedVersion\":2}")).jsonObject
        for (key in listOf("recipientCount", "totalGold", "totalXp", "totalGems")) assertTrue(preview.getValue(key).jsonPrimitive.isString, key)
        assertEquals("100", preview.getValue("totalGold").jsonPrimitive.content)
        assertEquals("7", preview.getValue("totalGems").jsonPrimitive.content)
        val publishBody = Json.encodeToString(GiftPublishRequest(2, preview.getValue("previewToken").jsonPrimitive.content, "exact-publish", "Test"))
        assertCounters(mutate("$base/$id/publish", publishBody))
        assertCounters(mutate("$base/$id/publish", publishBody)) // replay follows the same HTTP projection
        assertCounters(mutate("$base/$id/cancel", Json.encodeToString(GiftCancelRequest(3, "exact-cancel", "Test"))))
        val detail = client.get("$base/$id") { header(HttpHeaders.Cookie, "fasttowin_admin=${f.owner.token}") }
        assertCounters(detail.bodyAsText())
        val list = client.get(base) { header(HttpHeaders.Cookie, "fasttowin_admin=${f.owner.token}") }
        val page = Json.parseToJsonElement(list.bodyAsText()).jsonObject
        assertTrue(page.getValue("total").jsonPrimitive.isString)
        assertEquals(large.toString(), page.getValue("total").jsonPrimitive.content)
        assertCounters(page.getValue("items").jsonArray.single().toString())
    }
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
