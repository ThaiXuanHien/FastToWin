package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.gameModule
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlin.test.*

class AdminAssetsTest {
    @Test fun `disabled admin exposes neither console nor assets`() = testApplication {
        application { gameModule() }
        for (path in listOf("/admin/", "/admin/index.html", "/admin/admin.css", "/admin/admin.js", "/admin/admin-network.js", "/admin/admin-gifts.js", "/admin/admin-analytics.js", "/admin/admin-events.js")) {
            assertEquals(HttpStatusCode.NotFound, client.get(path).status)
        }
    }
    @Test fun `enabled console serves only named resources with browser security headers`() = testApplication {
        val fixture = AdminFixture()
        application { gameModule(adminService = fixture.service) }
        for ((path, type) in listOf("/admin/" to "text/html", "/admin/index.html" to "text/html", "/admin/admin.css" to "text/css", "/admin/admin.js" to "application/javascript", "/admin/admin-network.js" to "application/javascript", "/admin/admin-gifts.js" to "application/javascript", "/admin/admin-analytics.js" to "application/javascript", "/admin/admin-events.js" to "application/javascript")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.OK, response.status, path)
            assertTrue(response.bodyAsText().isNotBlank())
            assertTrue(response.headers[HttpHeaders.ContentType]!!.startsWith(type))
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertEquals("nosniff", response.headers["X-Content-Type-Options"])
            val csp = response.headers["Content-Security-Policy"]!!
            assertTrue(csp.contains("default-src 'none'"))
            assertTrue(csp.contains("script-src 'self'"))
            assertTrue(csp.contains("connect-src 'self'"))
            assertTrue(csp.contains("frame-ancestors 'none'"))
            assertFalse(csp.contains("unsafe-inline"))
            assertFalse(csp.contains("unsafe-eval"))
        }
        for (path in listOf("/admin/unknown.js", "/admin/AdminModels.kt", "/admin/../application.conf", "/admin/%2e%2e/application.conf", "/admin/sub/index.html")) {
            assertEquals(HttpStatusCode.NotFound, client.get(path).status, path)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/api/accounts").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/api/users") { header(HttpHeaders.Authorization, "Bearer player-token") }.status)
    }
}
