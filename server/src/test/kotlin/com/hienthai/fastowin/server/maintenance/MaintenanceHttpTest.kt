package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MaintenanceHttpTest {
    @Test fun oldSocketCannotAdmitIntentAfterReadFailureAndEmergencyClosesItWithinFiveSeconds() = testApplication {
        val f = MaintenanceFixture(); runBlocking { f.initialize() }
        val engine = GameEngine(); var failing = false
        val service = MaintenanceService(object : MaintenanceRepository {
            override suspend fun <T> transaction(block: MaintenanceTransaction.() -> T): T {
                if (failing) throw java.sql.SQLException("private SQL")
                return f.repository.transaction(block)
            }
        }, f.admin, { f.now })
        application { gameModule(engine = engine, maintenanceService = service) }
        val wsClient = createClient { install(WebSockets) }
        val socket = wsClient.webSocketSession("/game")
        socket.send(ProtocolJson.encodeToString<ClientMessage>(ClientMessage.ConnectGuest("old socket")))
        suspend fun receive(): ServerMessage = ProtocolJson.decodeFromString((socket.incoming.receive() as Frame.Text).readText())
        withTimeout(5000) { while (receive() !is ServerMessage.SessionReady) { } }
        failing = true
        withTimeout(5000) { while (engine.newIntentAllowed()) delay(20) }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/status").status)
        socket.send(ProtocolJson.encodeToString<ClientMessage>(ClientMessage.CreateRoom("blocked", "", ProtocolGameMode.ORDER)))
        val error = withTimeout(5000) {
            var message = receive()
            while (message !is ServerMessage.Error) message = receive()
            message
        }
        assertEquals("MAINTENANCE", error.code)
        failing = false
        f.service.update(f.owner, f.request().copy(mode = MaintenanceMode.EMERGENCY))
        assertEquals(CloseReason.Codes.GOING_AWAY.code, withTimeout(5000) { socket.closeReason.await() }!!.code)
    }
    @Test fun statusAliasesUseSameLiveProviderAndSafeUnavailablePath() = testApplication {
        var fail = false
        application { gameModule(serviceStatusProvider = {
            if (fail) throw IllegalStateException("secret database password")
            ServiceStatusResponse(maintenance = true, message = "Maintenance")
        }) }
        val first = client.get("/status")
        assertEquals(first.bodyAsText(), client.get("/api/status").bodyAsText())
        assertEquals("no-store", first.headers[HttpHeaders.CacheControl])
        fail = true
        for (path in listOf("/status", "/api/status")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertFalse(response.bodyAsText().contains("password"))
        }
    }

    @Test fun durableClosureBlocksAuthNewIntentButNotRefreshLogoutHealthOrAdmin() = testApplication {
        val f = MaintenanceFixture(); runBlocking {
            f.initialize(); f.service.update(f.owner, f.request().copy(mode = MaintenanceMode.EMERGENCY))
        }
        application { gameModule(maintenanceService = f.service, adminService = f.admin) }
        for (path in listOf("/auth/register", "/auth/login", "/auth/upgrade-guest")) {
            assertEquals(HttpStatusCode.ServiceUnavailable, client.post(path) { contentType(ContentType.Application.Json); setBody("{}") }.status)
        }
        for (path in listOf("/auth/refresh", "/auth/logout")) {
            assertNotEquals(HttpStatusCode.ServiceUnavailable, client.post(path) { contentType(ContentType.Application.Json); setBody("{}") }.status)
        }
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals(HttpStatusCode.OK, client.get("/admin/api/maintenance") { header(HttpHeaders.Cookie, "fasttowin_admin=${f.owner.token}") }.status)
        assertTrue(client.get("/api/status").bodyAsText().contains("MAINTENANCE"))
    }
}
