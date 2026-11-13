package com.hienthai.fastowin.server.ownership

import io.ktor.client.request.get
import io.ktor.client.request.post
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal val productionWorkerPublicPaths = listOf("/game", "/auth/login", "/auth/register",
    "/api/content-terms", "/api/announcements", "/admin", "/admin/api/maintenance", "/gifts", "/status")

internal data class ProductionWorkerRoute(val path: String, val get: Int, val post: Int)

/** An earned preflight only; the full topology/presence/drain acceptance remains a separate gate. */
internal suspend fun runProductionTopologyPreflight(fixture: RoomAcceptanceFixture): JsonObject {
    check(fixture.settings.soleIngress)
    val generation = checkNotNull(fixture.settings.topologyStatus).generation
    fixture.refreshOwnerProcesses()
    val routes = productionWorkerPublicPaths.map { path ->
        ProductionWorkerRoute(path, fixture.client.get(fixture.settings.urls[1] + path).status.value,
            fixture.client.post(fixture.settings.urls[1] + path).status.value)
    }
    check(routes.all { it.get == 404 && it.post == 404 }) { "Worker exposes a public application route" }
    val owners = mutableListOf<Int>()
    for (owner in 0..1) {
        // Keep the fault pair and its production create limits independent of these probes.
        val match = fixture.createMatch(18 + owner, targetOwner = owner)
        try {
            check(match.socket(0).backend == 0 && match.socket(1).backend == 0)
            val before = fixture.snapshot(match.roomId)
            owners += fixture.ownerFor(before)
            val request = "topology-" + java.util.UUID.randomUUID()
            match.select(match.nextNumber, request)
            val committed = fixture.snapshot(match.roomId)
            check(committed.snapshot.selectionEvents.count { it.requestId == request } == 1)
            check(committed.instanceId == before.instanceId && committed.epoch == before.epoch &&
                committed.revision > before.revision)
            match.verifyEconomy()
        } finally { match.close() }
        withTimeout(25_000) {
            while (fixture.number("SELECT count(*) FROM room_ownership_pilot_memberships WHERE room_id='${match.roomId}'") != 0L) delay(50)
        }
    }
    return productionTopologyPreflightProjection(generation, fixture.publicSocketCounts(), owners, routes)
}

internal fun productionTopologyPreflightProjection(generation: Long, publicSockets: List<Int>,
    owners: List<Int>, routes: List<ProductionWorkerRoute>): JsonObject {
    check(generation > 0 && publicSockets.size == 2 && publicSockets[0] >= 4 && publicSockets[1] == 0)
    check(owners == listOf(0, 1))
    check(routes.map { it.path } == productionWorkerPublicPaths && routes.all { it.get == 404 && it.post == 404 })
    return buildJsonObject {
        put("admissionGeneration", generation)
        put("publicIngress", "a"); put("privateWorker", "b"); put("signedProcessesVerified", 2)
        put("publicSocketsByBackend", Json.encodeToJsonElement(publicSockets))
        put("goldenOwners", Json.encodeToJsonElement(owners))
        put("workerRoutes", JsonArray(routes.map { route -> buildJsonObject {
            put("path", route.path); put("get", route.get); put("post", route.post)
        } }))
    }
}