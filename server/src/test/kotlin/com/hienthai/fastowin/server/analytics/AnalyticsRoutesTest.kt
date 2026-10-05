package com.hienthai.fastowin.server.analytics

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.gameModule
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AnalyticsRoutesTest {
    @Test fun missingDatabaseReturnsSafeUnavailableInsteadOfEmptyAnalytics() = testApplication {
        val f = AdminFixture()
        val login = runBlocking { f.owner(); f.login() }
        application { gameModule(adminService = f.service) }
        val response = client.get("/admin/api/analytics?metric=REGISTRATIONS") {
            header(HttpHeaders.Cookie, "$ADMIN_COOKIE=${login.token}")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
    }
}
