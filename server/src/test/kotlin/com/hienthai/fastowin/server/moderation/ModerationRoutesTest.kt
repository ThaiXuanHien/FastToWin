package com.hienthai.fastowin.server.moderation

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.admin.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.*

class ModerationRoutesTest {
    @Test fun `admin queue requires session and never exposes reporter identity`()=testApplication {
        val admins=InMemoryAdminRepository()
        val authority=AdminService(admins,AdminConfiguration("http://localhost:8080",false),PasswordHasher(iterations=1_000))
        val repo=InMemoryModerationRepository(admins)
        val service=ModerationService(repo,authority)
        val reporter=UUID.randomUUID().toString();val target=UUID.randomUUID().toString()
        val login=runBlocking {
            authority.bootstrapOwner("owner@example.com","GoodPassword123!")
            admins.seedUser(UserDetail(reporter,"Reporter",null,"GUEST","ACTIVE",0,1,0,0,0,1000,null,null,null))
            repo.transaction {putTarget(UgcTarget(UgcTargetType.PLAYER_PROFILE,target,target,"<script>unsafe</script>","1"))}
            service.report(reporter,ContentReportRequest(UgcTargetType.PLAYER_PROFILE,target,UgcReportReason.OTHER,"Private","route-report"))
            authority.login("owner@example.com","GoodPassword123!","http://localhost:8080","127.0.0.1")
        }
        application {install(ContentNegotiation){json()};routing {moderationRoutes(service)}}
        assertEquals(HttpStatusCode.Unauthorized,client.get("/admin/api/moderation/reports").status)
        val response=client.get("/admin/api/moderation/reports"){header(HttpHeaders.Cookie,"$ADMIN_COOKIE=${login.token}")}
        assertEquals(HttpStatusCode.OK,response.status)
        assertEquals("no-store",response.headers[HttpHeaders.CacheControl])
        assertFalse(response.bodyAsText().contains(reporter))
        assertFalse(response.bodyAsText().contains("reporterId"))
        assertEquals(HttpStatusCode.BadRequest,client.get("/admin/api/moderation/reports?page=-1"){header(HttpHeaders.Cookie,"$ADMIN_COOKIE=${login.token}")}.status)
    }
    @Test fun `mutations reject unknown fields oversized bodies bad origin and missing csrf`()=testApplication {
        val admins=InMemoryAdminRepository()
        val authority=AdminService(admins,AdminConfiguration("http://localhost:8080",false),PasswordHasher(iterations=1_000))
        val repo=InMemoryModerationRepository(admins)
        val service=ModerationService(repo,authority)
        val reporter=UUID.randomUUID().toString()
        lateinit var reportId:String
        val login=runBlocking {
            authority.bootstrapOwner("owner@example.com","GoodPassword123!")
            admins.seedUser(UserDetail(reporter,"Reporter",null,"GUEST","ACTIVE",0,1,0,0,0,1000,null,null,null))
            repo.transaction {putTarget(UgcTarget(UgcTargetType.PLAYER_PROFILE,reporter,reporter,"Name","1"))}
            reportId=service.report(reporter,ContentReportRequest(UgcTargetType.PLAYER_PROFILE,reporter,UgcReportReason.OTHER,requestId="route-report")).reportId
            authority.login("owner@example.com","GoodPassword123!","http://localhost:8080","127.0.0.1")
        }
        application {install(ContentNegotiation){json()};routing {moderationRoutes(service)}}
        val body="""{"expectedVersion":1,"requestId":"review","reason":"Review","status":"REVIEWING"}"""
        suspend fun post(bodyValue:String=body,origin:String="http://localhost:8080",csrf:String?=login.response.csrfToken)=client.post("/admin/api/moderation/reports/$reportId/resolve") {
            contentType(ContentType.Application.Json);header(HttpHeaders.Cookie,"$ADMIN_COOKIE=${login.token}")
            header(HttpHeaders.Origin,origin);csrf?.let {header(ADMIN_CSRF_HEADER,it)};setBody(bodyValue)
        }
        assertEquals(HttpStatusCode.Forbidden,post(origin="https://untrusted.test").status)
        assertEquals(HttpStatusCode.Forbidden,post(csrf=null).status)
        assertEquals(HttpStatusCode.BadRequest,post(body.dropLast(1)+",\"reporterId\":\"forged\"}").status)
        assertEquals(HttpStatusCode.BadRequest,post(" ".repeat(8193)).status)
        assertEquals(HttpStatusCode.OK,post().status)
    }
    @Test fun `disabled admin route stays absent`()=testApplication {
        application {routing {moderationRoutes(ModerationService(InMemoryModerationRepository()),false)}}
        assertEquals(HttpStatusCode.NotFound,client.get("/admin/api/moderation/reports").status)
    }
}
