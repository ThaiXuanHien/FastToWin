package com.hienthai.fastowin.server.analytics

import com.hienthai.fastowin.server.admin.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.analyticsRoutes(service: AnalyticsService) {
    get("/admin/api/analytics") {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.response.header("X-Content-Type-Options", "nosniff")
        try {
            call.respond(service.read(call.request.cookies[ADMIN_COOKIE], call.request.header(HttpHeaders.Origin),
                call.request.queryParameters.entries().associate { it.key to it.value }))
        } catch (error: AdminException) {
            call.respond(HttpStatusCode.fromValue(error.status), AdminError(error.code, error.message))
        }
    }
}
