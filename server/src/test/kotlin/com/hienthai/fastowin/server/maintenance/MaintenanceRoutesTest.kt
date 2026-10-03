package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.server.gameModule
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class MaintenanceRoutesTest {
    @Test fun protectedRoutesValidateAuthorizationBodyAndDoNotCache() = testApplication {
        val f=MaintenanceFixture(); runBlocking { f.initialize() }
        application { gameModule(adminService=f.admin,maintenanceService=f.service) }
        val path="/admin/api/maintenance"
        assertEquals(HttpStatusCode.Unauthorized,client.get(path).status)
        val read=client.get(path) { header(HttpHeaders.Cookie,"fasttowin_admin=${f.owner.token}") }
        assertEquals(HttpStatusCode.OK,read.status); assertEquals("no-store",read.headers[HttpHeaders.CacheControl])
        val wrongOrigin=client.post(path) { header(HttpHeaders.Origin,"http://localhost:3000"); contentType(ContentType.Application.Json); setBody("{") }
        assertEquals(HttpStatusCode.Forbidden,wrongOrigin.status)
        suspend fun send(body:String,csrf:String?=f.owner.csrfToken,type:ContentType=ContentType.Application.Json)=client.post(path) {
            header(HttpHeaders.Origin,f.owner.origin!!); header(HttpHeaders.Cookie,"fasttowin_admin=${f.owner.token}")
            csrf?.let { header("X-Admin-CSRF",it) }; contentType(type); setBody(body)
        }
        assertEquals(HttpStatusCode.Forbidden,send(Json.encodeToString(f.request()),null).status)
        for (body in listOf("{", "{}", Json.encodeToString(f.request()).dropLast(1)+",\"unknown\":true}","x".repeat(16_385))) {
            assertEquals(HttpStatusCode.BadRequest,send(body).status)
        }
        assertEquals(HttpStatusCode.BadRequest,send("{}",type=ContentType.Text.Plain).status)
        val request=f.request(); val result=send(Json.encodeToString(request))
        assertEquals(HttpStatusCode.OK,result.status); assertEquals("no-store",result.headers[HttpHeaders.CacheControl])
        assertEquals(1,Json.decodeFromString<MaintenanceAdminResponse>(result.bodyAsText()).persisted.version)
        assertEquals(result.bodyAsText(),send(Json.encodeToString(request)).bodyAsText())
        assertEquals(HttpStatusCode.Conflict,send(Json.encodeToString(f.request())).status)
    }
    @Test fun requestedDurableControlWithoutDatabaseIsSafeUnavailable() = testApplication {
        val f=MaintenanceFixture(); runBlocking { f.initialize() }
        application { gameModule(adminService=f.admin,maintenanceService=MaintenanceService(UnavailableMaintenanceRepository,f.admin)) }
        val response=client.get("/admin/api/maintenance") { header(HttpHeaders.Cookie,"fasttowin_admin=${f.owner.token}") }
        assertEquals(HttpStatusCode.ServiceUnavailable,response.status)
        assertEquals("no-store",response.headers[HttpHeaders.CacheControl])
        assertFalse(response.bodyAsText().contains("SELECT"))
    }
    @Test fun maintenanceRoutesRemainUnwiredUntilRuntimeIntegration() = testApplication {
        application { gameModule() }
        assertEquals(HttpStatusCode.NotFound,client.get("/admin/api/maintenance").status)
    }
}
