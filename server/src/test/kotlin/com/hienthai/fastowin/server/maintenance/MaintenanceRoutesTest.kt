package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.GameEngine
import com.hienthai.fastowin.server.admin.InMemoryAdminRepository
import com.hienthai.fastowin.server.gameModule
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

class MaintenanceRoutesTest {
    @Test fun disabledAdminKeepsDurableRuntimeWithoutRegisteringMaintenanceRoutes() = testApplication {
        val start = 1_800_000_000_000L
        val now = AtomicLong(start)
        val repository = MemoryMaintenanceRepository(InMemoryAdminRepository())
        runBlocking {
            repository.observationTransaction {
                putSnapshot(0, MaintenanceSnapshot(
                    version = 7, state = MaintenanceState.ANNOUNCING, mode = MaintenanceMode.PLANNED,
                    messageVi = "Bảo trì", messageEn = "Maintenance", changedAtEpochMillis = start,
                    startedAtEpochMillis = start, transitionAtEpochMillis = start + 60_000,
                    drainDeadlineEpochMillis = start + 120_000, warningMinutes = 1, drainMinutes = 1
                ))
            }
        }
        val service = MaintenanceService(repository, admins = null, nowMillis = now::get)
        val engine = GameEngine()
        // Fixed clocks keep worker observations idempotent and prevent watchdog/scheduler races.
        val runtime = MaintenanceRuntime(engine, service, monotonicMillis = { 0 })
        application {
            gameModule(engine = engine, adminService = null, maintenanceService = service, maintenanceRuntime = runtime)
        }
        startApplication()
        val expectedPolicy = MaintenancePolicy(
            MaintenanceState.ANNOUNCING, MaintenanceMode.PLANNED, 7, "Bảo trì", "Maintenance", start + 60_000
        )
        val expectedStatus = ServiceStatusResponse(
            maintenance = false, message = "Bảo trì", pollAfterSeconds = 5,
            state = MaintenanceState.ANNOUNCING, mode = MaintenanceMode.PLANNED, version = 7,
            messageVi = "Bảo trì", messageEn = "Maintenance", transitionAtEpochMillis = start + 60_000
        )
        assertEquals(expectedPolicy, service.effectivePolicy())
        assertEquals(expectedStatus, runtime.publicStatus())
        assertTrue(engine.newIntentAllowed())

        val path = "/admin/api/maintenance"
        val get = client.get(path)
        val post = client.post(path) { contentType(ContentType.Application.Json); setBody("{}") }
        assertEquals(listOf(HttpStatusCode.NotFound, HttpStatusCode.NotFound), listOf(get.status, post.status))
        assertEquals(expectedPolicy, service.effectivePolicy())
        assertEquals(expectedStatus, runtime.publicStatus())
        assertEquals(7, repository.observationTransaction { snapshot().version })
        for (statusPath in listOf("/status", "/api/status")) {
            val response = client.get(statusPath)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertEquals(expectedStatus, ProtocolJson.decodeFromString<ServiceStatusResponse>(response.bodyAsText()))
        }

        now.set(start + 60_000)
        runtime.refresh()
        assertEquals(MaintenanceState.MAINTENANCE, service.effectivePolicy().state)
        assertTrue(service.effectivePolicy().available)
        assertEquals(9, repository.observationTransaction { snapshot().version })
        assertFalse(engine.newIntentAllowed())
        assertTrue(runtime.publicStatus().maintenance)
        for (statusPath in listOf("/status", "/api/status")) {
            val response = client.get(statusPath)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            val status = ProtocolJson.decodeFromString<ServiceStatusResponse>(response.bodyAsText())
            assertEquals(MaintenanceState.MAINTENANCE, status.state)
            assertEquals(9, status.version)
            assertTrue(status.maintenance)
        }
    }
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
