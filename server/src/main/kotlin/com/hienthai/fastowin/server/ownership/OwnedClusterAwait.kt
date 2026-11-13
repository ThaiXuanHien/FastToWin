package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
internal data class OwnedAwaitReceipt(val operationId: String, val outcome: String,
    val lastStatus: OwnedControlResponse? = null, val completion: OwnedControlResponse? = null)

/** Client-side bounded progress. A timeout is ambiguous and must be reconciled through new status. */
internal suspend fun awaitClusterDrain(operationId: UUID, generation: Long, timeoutMillis: Long,
    coordinator: UUID, identity: OwnedCandidateIdentity, binding: OwnedAdmissionBinding, pollMillis: Long = 500,
    send: suspend (OwnedControlRequest) -> OwnedControlResponse?): OwnedAwaitReceipt {
    require(generation > 0 && timeoutMillis in 1..3_600_000 && pollMillis > 0)
    var currentGeneration = generation
    var lastStatus: OwnedControlResponse? = null
    var lastFingerprint: Triple<String, Long, UUID>? = null
    var stepId = UUID.randomUUID()
    fun receipt(outcome: String, completion: OwnedControlResponse? = null) =
        OwnedAwaitReceipt(operationId.toString(), outcome, lastStatus, completion)
    return withTimeoutOrNull(timeoutMillis) {
        while (true) {
            val statusRequest = OwnedControlRequest(UUID.randomUUID().toString(), "status", currentGeneration,
                candidateSha256 = identity.candidateSha256, topologySha256 = binding.topologySha256)
            val status = send(statusRequest) ?: return@withTimeoutOrNull receipt("unavailable")
            val policy = status.policy ?: return@withTimeoutOrNull receipt("unavailable")
            if (policy.topologySha256 != binding.topologySha256 || policy.policySha256 != binding.policySha256)
                return@withTimeoutOrNull receipt("unavailable")
            if (status.outcome == "stale") {
                currentGeneration = policy.generation
                delay(pollMillis)
                continue
            }
            val process = status.processId?.let(::parsePilotUuid)
            if (status.outcome != "ok" || status.nodeId != coordinator.toString() || process == null ||
                status.candidateSha256 != identity.candidateSha256 || status.topologySha256 != binding.topologySha256 ||
                status.generation != policy.generation || status.openRooms == null || status.pendingSettlements == null ||
                status.liveLeases == null || status.openRooms < 0 || status.pendingSettlements < 0 || status.liveLeases < 0)
                return@withTimeoutOrNull receipt("unavailable")
            lastStatus = status
            currentGeneration = policy.generation
            val operation = if (policy.state == OwnedAdmissionState.ACTIVE) "stop_new" else "step"
            val fingerprint = Triple(operation, currentGeneration, process)
            if (fingerprint != lastFingerprint) { stepId = UUID.randomUUID(); lastFingerprint = fingerprint }
            val step = OwnedControlRequest(stepId.toString(), operation, currentGeneration,
                process.toString(), identity.candidateSha256, binding.topologySha256)
            val response = send(step) ?: return@withTimeoutOrNull receipt("unavailable")
            when (response.outcome) {
                "completed" -> {
                    if (response.proof == null || response.policy?.state != OwnedAdmissionState.DISABLED)
                        return@withTimeoutOrNull receipt("unavailable")
                    return@withTimeoutOrNull receipt("completed", response)
                }
                "ok", "pending", "stale" -> { currentGeneration = response.policy?.generation ?: currentGeneration; delay(pollMillis) }
                else -> return@withTimeoutOrNull receipt("unavailable")
            }
        }
        @Suppress("UNREACHABLE_CODE") receipt("unavailable")
    } ?: receipt("deadline")
}
