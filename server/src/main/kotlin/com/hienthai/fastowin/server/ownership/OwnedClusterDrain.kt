package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.CancellationException
import java.sql.SQLException
import java.util.UUID

internal data class OwnedNodeDrainAck(
    val nodeId: UUID,
    val processId: UUID,
    val generation: Long,
    val candidateSha256: String,
    val topologySha256: String,
    val quiesced: Boolean,
    val snapshot: RoomOwnershipDurableSnapshot
)

internal enum class OwnedProofMode { INITIAL_DRAIN, REVALIDATION }

internal data class OwnedRollbackProof(
    val mode: OwnedProofMode,
    val candidateSha256: String,
    val topologySha256: String,
    val acknowledgedPolicy: OwnedAdmissionPolicy,
    val disabledPolicy: OwnedAdmissionPolicy,
    val nodes: List<OwnedNodeDrainAck>,
    val snapshot: RoomOwnershipDurableSnapshot
)

internal sealed interface OwnedDrainOutcome {
    data class Completed(val proof: OwnedRollbackProof) : OwnedDrainOutcome
    data class Incomplete(val policy: OwnedAdmissionPolicy, val snapshot: RoomOwnershipDurableSnapshot?) : OwnedDrainOutcome
    data class Unavailable(val policy: OwnedAdmissionPolicy?) : OwnedDrainOutcome
}

/** Implementations must return only local state or freshly authenticated replies from the configured peer. */
internal interface OwnedDrainNode {
    val nodeId: UUID
    suspend fun status(generation: Long): OwnedNodeDrainAck?
    suspend fun closeWaiting(generation: Long): Boolean
    suspend fun quiesce(generation: Long): OwnedNodeDrainAck?
}

internal class OwnedClusterDrain(
    private val binding: OwnedAdmissionBinding,
    private val candidate: String,
    private val local: OwnedDrainNode,
    private val peer: OwnedDrainNode,
    private val observe: suspend () -> OwnedAdmissionPolicy?,
    private val snapshot: suspend () -> RoomOwnershipDurableSnapshot,
    private val advance: suspend (OwnedAdmissionPolicy, OwnedAdmissionState) -> OwnedAdmissionPolicy,
    private val verifyDisabled: suspend (OwnedAdmissionPolicy) -> RoomOwnershipDurableSnapshot?
) {
    init {
        require(local.nodeId != peer.nodeId && candidate.matches(Regex("[0-9a-f]{64}")))
    }

    suspend fun step(expectedGeneration: Long): OwnedDrainOutcome {
        var policy: OwnedAdmissionPolicy? = null
        try {
            policy = observe() ?: return OwnedDrainOutcome.Unavailable(null)
            if (policy.generation != expectedGeneration || policy.topologySha256 != binding.topologySha256 ||
                policy.policySha256 != binding.policySha256 ||
                policy.state !in setOf(OwnedAdmissionState.STOP_NEW, OwnedAdmissionState.QUIESCING, OwnedAdmissionState.DISABLED)
            ) return OwnedDrainOutcome.Incomplete(policy, null)
            if (policy.state == OwnedAdmissionState.STOP_NEW) {
                val first = listOf(local.status(policy.generation), peer.status(policy.generation))
                if (first.any { it == null }) return OwnedDrainOutcome.Unavailable(policy)
                if (!valid(first.filterNotNull(), policy.generation, false)) return OwnedDrainOutcome.Incomplete(policy, null)
                if (!local.closeWaiting(policy.generation) || !peer.closeWaiting(policy.generation)) {
                    return OwnedDrainOutcome.Unavailable(policy)
                }
                val current = snapshot()
                if (!current.empty()) return OwnedDrainOutcome.Incomplete(policy, current)
                policy = advance(policy, OwnedAdmissionState.QUIESCING)
            }
            val acks = listOf(local.quiesce(policy.generation), peer.quiesce(policy.generation))
            if (acks.any { it == null }) return OwnedDrainOutcome.Unavailable(policy)
            val acked = acks.filterNotNull()
            if (!valid(acked, policy.generation, true)) return OwnedDrainOutcome.Incomplete(policy, null)
            val fresh = listOf(local.status(policy.generation), peer.status(policy.generation))
            if (fresh.any { it == null }) return OwnedDrainOutcome.Unavailable(policy)
            val observed = fresh.filterNotNull()
            if (!valid(observed, policy.generation, true) ||
                observed.map { it.nodeId to it.processId }.toSet() != acked.map { it.nodeId to it.processId }.toSet()
            ) return OwnedDrainOutcome.Incomplete(policy, null)
            val revalidation = policy.state == OwnedAdmissionState.DISABLED
            val finalSnapshot = if (revalidation) verifyDisabled(policy) ?: return OwnedDrainOutcome.Unavailable(policy) else snapshot()
            if (!finalSnapshot.empty()) return OwnedDrainOutcome.Incomplete(policy, finalSnapshot)
            val disabled = if (revalidation) policy else advance(policy, OwnedAdmissionState.DISABLED)
            return OwnedDrainOutcome.Completed(OwnedRollbackProof(if (revalidation) OwnedProofMode.REVALIDATION else OwnedProofMode.INITIAL_DRAIN, candidate, binding.topologySha256, policy,
                disabled, observed, finalSnapshot))
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: SQLException) {
            return OwnedDrainOutcome.Unavailable(policy)
        } catch (_: OwnedAdmissionRejected) {
            return OwnedDrainOutcome.Unavailable(policy)
        }
    }

    private fun valid(acks: List<OwnedNodeDrainAck>, generation: Long, requireQuiesced: Boolean): Boolean =
        acks.size == 2 && acks.map { it.nodeId }.toSet() == setOf(local.nodeId, peer.nodeId) &&
            acks.all { it.generation == generation && it.candidateSha256 == candidate &&
                it.topologySha256 == binding.topologySha256 && (!requireQuiesced || (it.quiesced && it.snapshot.empty())) }
}

private fun RoomOwnershipDurableSnapshot.empty() =
    remainingOwnedLeases == 0 && remainingOpenRooms == 0 && pendingSettlements == 0
