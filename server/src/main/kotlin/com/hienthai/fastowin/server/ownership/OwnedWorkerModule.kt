package com.hienthai.fastowin.server.ownership

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Private ownership process. No legacy engine, public routes, or application singleton jobs are assembled.
 * Readiness observes the committed production admission policy on every request.
 */
internal fun Application.ownedWorkerModule(
    runtime: RoomOwnershipPilot
) {
    require(runtime.isProductionMode) { "An owned worker requires production room ownership" }
    launch { runtime.delivery.run() }
    launch { runtime.runWorker() }
    launch { runtime.runPeerRefresh() }
    monitor.subscribe(ApplicationStopPreparing) {
        val drained = runBlocking {
            withTimeoutOrNull(15_000) {
                while (!runtime.beginDrain()) delay(100)
                true
            }
        } == true
        if (!drained) environment.log.warn("Owned worker drain did not finish before shutdown")
    }
    routing {
        runtime.installRoutes(this)
        get("/health") { call.respondText("OK") }
        get("/ready") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val ready = runtime.isReady()
            call.respondText(if (ready) "READY" else "UNAVAILABLE",
                status = if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable)
        }
        get("/internal/metrics") {
            val source = call.request.local.remoteAddress.asSocketAddress()
            if (source == null || !(source.isLoopbackAddress || source.isAllowedPeerSource(emptyList()))) {
                call.respondText("", status = HttpStatusCode.NotFound)
                return@get
            }
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondText(runtime.renderMetrics(), ContentType.parse("text/plain; version=0.0.4; charset=utf-8"))
        }
    }
}
