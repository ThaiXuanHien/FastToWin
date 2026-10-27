package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.server.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

internal fun configJson(rate:String="0.0001")="""{"goldRate":"$rate","gemRate":"1.5","rewards":[{"xp":30,"gold":300,"gems":3},{"xp":20,"gold":200,"gems":2},{"xp":10,"gold":100,"gems":1}]}"""
class ClanRaceRoutesPostgresTest {
    @Test fun `HTTP future config validates immutable economics auth current roles CSRF and version`()=runTest {
        raceDatabase {
            val f=this
            val session=player()
            testApplication {
                application { gameModule(authService=f.auth,adminService=f.admins,clanRaceService=ClanRaceService(PostgresClanRaceRepository(f.source),f.admins,{f.now}),giftDeliveryEnabled=false) }
                suspend fun mutation(path:String,body:String,method:HttpMethod=HttpMethod.Post,csrf:String=f.login.response.csrfToken,origin:String="https://admin.example")=client.request(path) {
                    this.method=method;contentType(ContentType.Application.Json);header(HttpHeaders.Origin,origin);header(HttpHeaders.Cookie,"fasttowin_admin=${f.login.token}");header("X-Admin-CSRF",csrf);setBody(body)
                }
                val root="/admin/api/clan-race"
                val draft="""{"weekStart":"2026-10-26","config":${configJson()},"reason":"Fixture","requestId":"create"}"""
                assertEquals(HttpStatusCode.Unauthorized,client.get("/api/clan-race").status)
                val inactive=client.get("/api/clan-race") { bearerAuth(session.accessToken) }
                assertEquals(HttpStatusCode.OK,inactive.status,inactive.bodyAsText())
                assertEquals("INACTIVE",Json.parseToJsonElement(inactive.bodyAsText()).jsonObject["state"]?.jsonPrimitive?.content)
                assertEquals("no-store",inactive.headers[HttpHeaders.CacheControl])
                assertEquals(HttpStatusCode.Forbidden,mutation(root,draft,csrf="bad").status)
                assertEquals(HttpStatusCode.Forbidden,mutation(root,draft,origin="https://evil.example").status)
                assertEquals(HttpStatusCode.BadRequest,mutation(root,draft.replace("2026-10-26","2026-10-27")).status)
                assertEquals(HttpStatusCode.BadRequest,mutation(root,draft.replace("0.0001","0.0000001")).status)
                assertEquals(HttpStatusCode.BadRequest,mutation(root,draft.replace("\"xp\":30","\"xp\":0")).status)
                val created=mutation(root,draft)
                assertEquals(HttpStatusCode.OK,created.status,created.bodyAsText())
                assertEquals(created.bodyAsText(),mutation(root,draft).bodyAsText())
                assertEquals(HttpStatusCode.Conflict,mutation(root,draft.replace("Fixture","Changed")).status)
                assertEquals(HttpStatusCode.Conflict,mutation("$root/2026-10-26/publish","""{"expectedVersion":99,"reason":"Publish","requestId":"stale"}""").status)
                f.execute("UPDATE admin_accounts SET role='OPERATOR' WHERE id='${f.actor.id}'")
                assertEquals(HttpStatusCode.Forbidden,mutation("$root/2026-10-26/publish","""{"expectedVersion":1,"reason":"Publish","requestId":"operator"}""").status)
                assertEquals(HttpStatusCode.Forbidden,mutation("$root/2026-10-26/draft",draft.replace("0.0001","0.1").replace("\"requestId\":\"create\"","\"requestId\":\"edit\",\"expectedVersion\":1"),HttpMethod.Put).status)
                f.execute("UPDATE admin_accounts SET role='SUPPORT' WHERE id='${f.actor.id}'")
                assertEquals(HttpStatusCode.Forbidden,client.get(root) { header(HttpHeaders.Cookie,"fasttowin_admin=${f.login.token}") }.status)
                f.execute("UPDATE admin_accounts SET role='OWNER' WHERE id='${f.actor.id}'")
                val published=mutation("$root/2026-10-26/publish","""{"expectedVersion":1,"reason":"Publish","requestId":"publish"}""")
                assertEquals(HttpStatusCode.OK,published.status,published.bodyAsText())
                assertEquals(HttpStatusCode.Conflict,mutation("$root/2026-10-26/draft",draft.replace("\"requestId\":\"create\"","\"requestId\":\"edit-published\",\"expectedVersion\":2"),HttpMethod.Put).status)
                assertEquals(HttpStatusCode.BadRequest,mutation(root,"x".repeat(32769)).status)
                assertEquals("1",f.scalar("SELECT count(*) FROM clan_race_revisions"))
                assertEquals("2",f.scalar("SELECT count(*) FROM admin_audit WHERE action LIKE 'CLAN_RACE_%'"))
            }
        }
    }
    @Test fun `HTTP operator clones previews unchanged config and owner archives only future weeks`()=runTest {
        raceDatabase {
            val f=this
            testApplication {
                application { gameModule(authService=f.auth,adminService=f.admins,clanRaceService=ClanRaceService(PostgresClanRaceRepository(f.source),f.admins,{f.now}),giftDeliveryEnabled=false) }
                suspend fun post(path:String,body:String)=client.post("/admin/api/clan-race$path") {
                    contentType(ContentType.Application.Json);header(HttpHeaders.Origin,"https://admin.example");header(HttpHeaders.Cookie,"fasttowin_admin=${f.login.token}");header("X-Admin-CSRF",f.login.response.csrfToken);setBody(body)
                }
                val draft="""{"weekStart":"2026-10-26","config":${configJson("0.010000")},"reason":"Fixture","requestId":"create"}"""
                assertEquals(HttpStatusCode.OK,post("",draft).status)
                f.execute("UPDATE admin_accounts SET role='OPERATOR' WHERE id='${f.actor.id}'")
                val cloned=post("/2026-10-26/clone","""{"weekStart":"2026-11-02","expectedVersion":1,"reason":"Clone unchanged","requestId":"clone"}""")
                assertEquals(HttpStatusCode.OK,cloned.status,cloned.bodyAsText())
                assertEquals("0.01",Json.decodeFromString<com.hienthai.fastowin.protocol.ClanRaceAdminView>(cloned.bodyAsText()).config.goldRate)
                val preview=post("/2026-11-02/preview","{}")
                assertEquals(HttpStatusCode.OK,preview.status,preview.bodyAsText())
                val value=Json.decodeFromString<com.hienthai.fastowin.protocol.ClanRacePreview>(preview.bodyAsText())
                assertEquals("1",value.gold100Points);assertEquals("3000",value.maximumBudget.xp);assertEquals("30000",value.maximumBudget.gold);assertEquals("300",value.maximumBudget.gems)
                f.execute("UPDATE admin_accounts SET role='OWNER' WHERE id='${f.actor.id}'")
                assertEquals(HttpStatusCode.OK,post("/2026-10-26/publish","""{"expectedVersion":1,"reason":"Publish","requestId":"publish"}""").status)
                assertEquals(HttpStatusCode.OK,post("/2026-10-26/archive","""{"expectedVersion":2,"reason":"Archive future","requestId":"archive"}""").status)
                f.advance("2026-11-02T06:00:00Z")
                f.login=f.admins.login(f.actor.email,"GoodPassword123!","https://admin.example","127.0.0.1")
                assertEquals(HttpStatusCode.Conflict,post("/2026-11-02/publish","""{"expectedVersion":1,"reason":"Too late","requestId":"late"}""").status)
                assertEquals(HttpStatusCode.NotFound,client.get("/admin/api/clan-race/2026-11-09") { header(HttpHeaders.Cookie,"fasttowin_admin=${f.login.token}") }.status)
                assertEquals(HttpStatusCode.BadRequest,post("",draft.replace("0.010000","1e2").replace("2026-10-26","2026-11-09").replace("\"requestId\":\"create\"","\"requestId\":\"invalid-rate\"")).status)
            }
        }
    }
    @Test fun `HTTP unavailable storage does not report inactive success and strict bodies reject client scores`()=runTest {
        raceDatabase {
            val f=this;val player=player()
            testApplication {
                application { gameModule(authService=f.auth,adminService=f.admins,giftDeliveryEnabled=false) }
                val response=client.get("/api/clan-race") { bearerAuth(player.accessToken) }
                assertEquals(HttpStatusCode.ServiceUnavailable,response.status);assertTrue(response.bodyAsText().contains("STORAGE_UNAVAILABLE"));assertEquals("no-store",response.headers[HttpHeaders.CacheControl])
                assertEquals(HttpStatusCode.BadRequest,client.post("/admin/api/clan-race") { contentType(ContentType.Application.Json);setBody("""{"weekStart":"2026-10-26","config":${configJson()},"reason":"Invalid client score","requestId":"strict","scoreUnits":"1000000"}""") }.status)
            }
        }
    }
}
