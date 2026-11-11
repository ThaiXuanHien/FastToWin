package com.hienthai.fastowin.server.ownership

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Base64

internal val rehearsalCases = listOf("kill-after-committed-selection","selective-owner-postgres-partition",
    "kill-before-selection-commit","kill-after-commit-before-reading-ack","start-response-loss","settlement-response-loss",
    "redis-process-pause-fails-closed","postgres-process-pause-no-optimistic-ack")

internal fun faultCaseProjection(result: RoomOwnershipFaultCaseReceipt, operations: List<Pair<String,String>>): JsonObject {
    require(result.passed && result.name in rehearsalCases && operations.size == 2)
    val (role, op) = operations.first()
    val (restoredRole, restore) = operations.last()
    require(restoredRole == role)
    val mechanism = when (result.name) {
        rehearsalCases[1] -> {
            require(role == "proxy" && op in setOf("block-a","block-b") && restore == "un$op")
            "proxy-partition"
        }
        rehearsalCases[6], rehearsalCases[7] -> {
            val store = if (result.name == rehearsalCases[6]) "redis" else "pg"
            require(role == store && op == "pause" && restore == "resume")
            if (store == "pg") "postgres-process-pause" else "redis-process-pause"
        }
        else -> {
            require(role in setOf("a","b") && op == "kill" && restore == "start")
            "backend-kill"
        }
    }
    val ownerCase = mechanism == "backend-kill" || mechanism == "proxy-partition"
    require(result.recoveryMillis in 1L..25000L)
    val before = requireNotNull(result.beforeEpoch)
    val after = requireNotNull(result.afterEpoch)
    require(before > 0 && after >= before && (!ownerCase || after > before))
    val proof = buildJsonObject {
        fun flag(key: String, expected: Boolean) {
            require(result.evidence[key]?.toBooleanStrictOrNull() == expected)
            put(key, expected)
        }
        fun number(key: String, range: LongRange) {
            val n = requireNotNull(result.evidence[key]?.toLongOrNull())
            require(n in range)
            put(key, n)
        }
        when (result.name) {
            rehearsalCases[0] -> { flag("acknowledgedSelectionPreserved",true); number("quotaDebits",2L..2) }
            rehearsalCases[1] -> { flag("staleRequestPersisted",false); number("postTakeoverEpochRegressions",0L..0) }
            rehearsalCases[2] -> { flag("controllerRowLockHeld",true); number("databaseBlockedSessions",1L..1000); number("requestCountAfterRetry",1L..1) }
            rehearsalCases[3] -> { flag("durableCommitObservedBeforeKill",true); number("requestCountAfterRetry",1L..1) }
            rehearsalCases[4] -> { flag("sameDurableMatchAndBoard",true); number("quotaDebitsAfterReadyRetry",2L..2) }
            rehearsalCases[5] -> {
                number("selectionCount",50L..50); number("quotaDebits",2L..2); number("matchRows",1L..1)
                number("settlementRows",1L..1); number("walletRows",2L..2)
            }
            rehearsalCases[6] -> {
                flag("newConnectionAdmittedDuringOutage",false); flag("idleAccountConnectedAfterRecovery",true); number("existingMatchQuotaDebits",2L..2)
            }
            rehearsalCases[7] -> {
                flag("outageAcceptedAck",false); flag("requestAbsentBeforeRecoveryRetry",true)
                number("requestCountAfterRecovery",1L..1); number("outageObservationMillis",0L..60000)
            }
        }
    }
    return buildJsonObject {
        put("name", result.name); put("mechanism", mechanism); put("passed", result.passed)
        put("beforeEpoch", before); put("afterEpoch", after)
        put("beforeRevision", requireNotNull(result.beforeRevision)); put("afterRevision", requireNotNull(result.afterRevision))
        put("recoveryMillis", JsonPrimitive(result.recoveryMillis)); put("proof", proof)
    }
}

internal fun rehearsalLoadProjection(load: LoadReceipt, operations: List<Pair<String,String>>): JsonObject {
    check(operations.size == 6 && operations.chunked(2).all { (pause, resume) ->
        pause.first in setOf("a","b") && pause.second == "pause" && resume == (pause.first to "resume")
    }) { "Missing verified load outage" }
    val pools = observedRehearsalPools(load.finalBackendMetrics)
    check(load.backendCount == 2 && load.fullAcceptanceProfile && load.configuredDurationSeconds == 600 &&
        load.concurrentRooms == 20 && load.selectionCount == 60000L && load.recoveryMillis.size == 3 &&
        load.recoveryMillis.all { it in 1L..25000 })
    // New local diagnostics must be explicitly reviewed before they can enter a provider receipt.
    val allowed = setOf("seed", "backendCount", "concurrentRooms", "configuredDurationSeconds",
        "configuredSelectionsPerRoomPerSecond", "roomSetupSeconds", "elapsedSeconds", "selectionCount",
        "selectionsByRoomSlot", "actualSelectionsPerSecond", "completedMatches", "commandP95Millis",
        "commandMaxMillis", "commandAttempts", "commandRetries", "minimumSelectionSpacingMillis",
        "maxScheduledLatenessMillis", "scheduledToAckP95Millis", "recoveryMillis", "recoveryP95Millis",
        "maxQueuedEventSeconds", "maxPostgresPoolSaturation", "queuedEventP95Seconds",
        "postgresPoolP95Saturation", "resourceSamples", "resourceSampleCount", "fullAcceptanceProfile")
    val numeric = Json.encodeToJsonElement(load).jsonObject.filterKeys { it in allowed }
    check(numeric.keys == allowed)
    return JsonObject(numeric + mapOf("pools" to Json.encodeToJsonElement(pools),
        "finalBackendMetrics" to Json.encodeToJsonElement(load.finalBackendMetrics.map(::baselineSafeMetrics))))
}

internal fun emitRehearsalReceipt(
    manifest: String, profile: String, kind: String, payload: JsonObject,
    jarSha256: String = REHEARSAL_JAR_SHA
): List<String> {
    require(manifest.matches(Regex("[0-9a-f]{64}")) && profile in setOf("two-backend","fault") &&
        kind in setOf("load","fault","latency","failure") && jarSha256.matches(Regex("[0-9a-f]{64}")))
    val bytes = buildJsonObject {
        put("schema",1); put("manifestSha256",manifest); put("jarSha256",jarSha256)
        put("kind",kind); put("profile",profile); put("payload",payload)
    }.toString().encodeToByteArray()
    require(bytes.size <= 262144)
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val parts = Base64.getEncoder().encodeToString(bytes).chunked(2048)
    return parts.mapIndexed { i, chunk -> "FTW_REHEARSAL_${kind.uppercase()} ${i+1}/${parts.size} $hash $chunk" }
}
