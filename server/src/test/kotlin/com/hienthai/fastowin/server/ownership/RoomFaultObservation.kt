package com.hienthai.fastowin.server.ownership

import kotlinx.serialization.json.*

internal class TopologyPhaseFailure(val phase: String, failure: Throwable) : IllegalStateException("Topology preflight failed", failure)

internal class LoadPhaseFailure(val phase: String, failure: Throwable) : IllegalStateException("Fixture load phase failed", failure)
internal class ObservedSocketClose(val backend: Int, val code: Int, reason: String?) : IllegalStateException("Fixture websocket closed") {
    val classification = when (reason) {
        "Service temporarily unavailable" -> "storage-unavailable"
        "Room recovery in progress" -> "recovering"
        "Account session revoked" -> "session-revoked"
        "Account restricted" -> "restricted"
        com.hienthai.fastowin.protocol.SESSION_REPLACED_CLOSE_REASON -> "session-replaced"
        "Player rate limit exceeded", "WebSocket rate limit exceeded", "Connection rate limit exceeded" -> "rate-limit"
        "Room owner draining", "Server maintenance" -> "draining"
        "fixture recycle" -> "recycle"
        null, "" -> "absent"
        else -> "other"
    }
}

/** Local sidecar only. Dynamic messages, identities and exception stacks never enter it. */
internal fun postgresPauseDiagnostic(stage: String, endedBy: String, elapsedMillis: Long, errors: Set<String>): String {
    require(stage in setOf("observation", "recovered"))
    require(endedBy in setOf("deadline", "socket-closed", "request-rejected"))
    require(elapsedMillis in 0..120_000)
    val codes = setOf("ROOM_RECOVERING", "ROOM_NOT_FOUND", "RATE_LIMITED", "UNAUTHORIZED", "INTERNAL_ERROR")
    return buildJsonObject {
        put("stage", stage)
        put("endedBy", endedBy)
        put("elapsedMillis", elapsedMillis)
        put("errorCodes", JsonArray(errors.map { it.takeIf(codes::contains) ?: "unknown" }.distinct().sorted().map(::JsonPrimitive)))
    }.toString()
}

internal fun rehearsalFailureDiagnostic(failure: Throwable): String {
    val knownFiles = mapOf(
        "RoomOwnershipLoadAcceptance.kt" to "load",
        "RoomOwnershipAcceptanceMain.kt" to "fixture",
        "RoomOwnershipFaultAcceptance.kt" to "fault",
        "RoomFaultControl.kt" to "control",
        "RailwayRoomRehearsal.kt" to "rehearsal",
        "RehearsalReceipts.kt" to "projection",
        "ProductionTopologyAcceptance.kt" to "topology",
        "ProductionPresenceAcceptance.kt" to "presence",
        "ProductionIngressAcceptance.kt" to "ingress",
        "ProductionDrainAcceptance.kt" to "drain",
        "ProductionLaneAcceptance.kt" to "lanes"
    )
    return buildJsonArray {
        for (cause in generateSequence(failure) { it.cause }.take(8)) {
            add(buildJsonObject {
                put("category", rehearsalFailureCategory(cause))
                if (cause is LoadPhaseFailure && cause.phase in setOf("initial_create", "recreate", "command", "economy", "close")) {
                    put("phase", cause.phase)
                }
                if (cause is AcceptanceRejected) {
                    val codes = setOf("ROOM_RECOVERING", "ROOM_NOT_FOUND", "RATE_LIMITED", "RATE_LIMIT_UNAVAILABLE",
                        "UNAUTHORIZED", "INTERNAL_ERROR", "INVALID_ROOM_NAME", "MAINTENANCE", "ALREADY_IN_ROOM",
                        "ROOM_DRAINING", "PILOT_ACCOUNT_REQUIRED", "INVALID_ACCESS_TOKEN", "AUTH_REQUIRED",
                        "PLAY_QUOTA_EXHAUSTED", "ROOM_NOT_WAITING", "ROOM_NOT_PLAYING", "INVALID_NUMBER",
                        "NOT_YOUR_TURN", "ACCOUNT_RESTRICTED", "INVALID_REQUEST_ID")
                    put("protocolCode", cause.code.takeIf(codes::contains) ?: "unknown")
                }
                if (cause is TopologyPhaseFailure) {
                    val phases = listOf("a", "b").flatMap { node -> listOf("connect_pair", "create_room", "join_room",
                        "ready_host", "start_match", "select", "economy", "close", "membership-release").map { "golden-$node-$it" } }
                    put("phase", cause.phase.takeIf(phases::contains) ?: "unknown")
                }
                if (cause is DrainAcceptanceFailure) {
                    val phases = setOf("prepare", "stop-request", "held-create", "held-policy", "overlapping-play",
                        "stop-committed", "late-create", "late-join", "late-start", "late-state", "stopped-ingress",
                        "stopped-worker", "stopped-gates", "stopped-public-create", "stopped-play",
                        "waiting-closure", "held-settlement", "closed-settlement", "settlement-released",
                        "worker-unavailable", "final-proof")
                    put("phase", cause.checkpoint.takeIf(phases::contains) ?: "unknown")
                }
                if (cause is ObservedSocketClose) {
                    put("backend", cause.backend)
                    put("code", cause.code)
                    put("reason", cause.classification)
                }
                put("sites", buildJsonArray {
                    cause.stackTrace.asSequence().mapNotNull { frame ->
                        val site = knownFiles[frame.fileName] ?: return@mapNotNull null
                        frame.lineNumber.takeIf { it in 1..9999 }?.let { "$site:$it" }
                    }.distinct().take(8).forEach { add(it) }
                })
            })
        }
    }.toString()
}
