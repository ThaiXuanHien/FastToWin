package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.sql.SQLException
import java.util.UUID

/** Private control is role-restricted; all durable decisions remain with the ingress authority. */
internal class OwnedClusterControl(
    private val config: RoomOwnershipRuntimeConfig,
    private val process: UUID,
    private val authority: PostgresOwnedAdmissionRepository,
    private val binding: OwnedAdmissionBinding,
    private val rooms: PostgresRoomOwnershipRepository,
    private val transport: OwnerPeerTransport,
    private val identity: () -> OwnedCandidateIdentity,
    private val closeWaiting: suspend (Long) -> Unit,
    private val closeGate: () -> Unit,
    private val beginDrain: suspend () -> Boolean,
    private val isDrained: () -> Boolean,
    private val isAccepting: () -> Boolean
) {
    private val lock = Mutex()
    private var quiescedGeneration: Long? = null
    private val cache = LinkedHashMap<OwnedControlRequest, OwnedControlResponse>()
    private val fingerprints = LinkedHashMap<Pair<String, String>, OwnedControlRequest>()

    suspend fun dispatch(request: OwnedControlRequest): OwnedControlResponse = lock.withLock {
        fun reply(outcome: String, policy: OwnedAdmissionPolicy? = null) =
            OwnedControlResponse(request.operationId, request.operation, request.generation, outcome, policy = policy)
        try {
            val candidate = identity()
            if (request.candidateSha256 != candidate.candidateSha256 || request.topologySha256 != binding.topologySha256 ||
                (request.operation != "status" && request.expectedProcessId != process.toString()) ||
                (request.expectedProcessId != null && request.expectedProcessId != process.toString())) return@withLock reply("mismatch")
            val policy = authority.observe() ?: return@withLock reply("unavailable")
            if (policy.topologySha256 != binding.topologySha256 || policy.policySha256 != binding.policySha256)
                return@withLock reply("mismatch", policy)
            if (request.operation in setOf("stop_new", "step", "resume")) {
                // Retain the current generation plus successful transition replay into it.
                fingerprints.entries.removeIf { (_, recorded) ->
                    recorded.generation != policy.generation && cache[recorded]?.policy != policy
                }
                cache.entries.removeIf { (recorded, response) ->
                    recorded.generation != policy.generation && response.policy != policy
                }
                if (request.generation != policy.generation && cache[request]?.policy != policy)
                    return@withLock reply("stale", policy)
                if (cache[request]?.policy != policy &&
                    ((request.operation == "step" && policy.state == OwnedAdmissionState.ACTIVE) ||
                     (request.operation == "resume" && policy.state == OwnedAdmissionState.ACTIVE) ||
                     (request.operation == "stop_new" && policy.state !in setOf(OwnedAdmissionState.ACTIVE, OwnedAdmissionState.STOP_NEW))))
                    return@withLock reply("stale", policy)
                val key = request.operationId to request.operation
                val prior = fingerprints[key]
                if (prior != null && prior != request) return@withLock reply("mismatch", policy)
                if (prior == null) {
                    if (fingerprints.size >= 128) return@withLock reply("unavailable", policy)
                    fingerprints[key] = request
                }
                if (request.operation != "step") {
                    val cached = cache[request]
                    if (cached != null) return@withLock if (cached.policy == policy) cached else reply("stale", policy)
                }
            }
            val ingress = config.runtimeRole == RoomOwnershipRuntimeRole.INGRESS
            when (request.operation) {
                "status" -> status(request, candidate, policy)
                "close_waiting" -> {
                    if (policy.generation != request.generation || policy.state != OwnedAdmissionState.STOP_NEW ||
                        request.expectedProcessId != process.toString()) return@withLock reply("stale", policy)
                    closeWaiting(request.generation)
                    reply("ok", authority.observe())
                }
                "quiesce" -> {
                    if (policy.generation != request.generation || policy.state !in setOf(OwnedAdmissionState.QUIESCING, OwnedAdmissionState.DISABLED) ||
                        request.expectedProcessId != process.toString()) return@withLock reply("stale", policy)
                    authority.fenceQuiesce(policy, binding, closeGate)
                    if (!beginDrain()) return@withLock reply("pending", policy)
                    quiescedGeneration = request.generation
                    status(request, candidate, authority.observe() ?: return@withLock reply("unavailable"))
                }
                "stop_new" -> {
                    if (!ingress) return@withLock reply("forbidden", policy)
                    val stopped = if (policy.state == OwnedAdmissionState.STOP_NEW && policy.generation == request.generation)
                        policy else authority.stopNew(request.generation)
                    reply("ok", stopped).also { cache[request] = it }
                }
                "resume" -> {
                    if (!ingress) return@withLock reply("forbidden", policy)
                    if (policy.generation != request.generation || policy.state !in setOf(OwnedAdmissionState.STOP_NEW,
                            OwnedAdmissionState.QUIESCING, OwnedAdmissionState.DISABLED)) return@withLock reply("stale", policy)
                    val prepared = if (policy.state == OwnedAdmissionState.STOP_NEW) policy else authority.prepareResume(policy, binding)
                    suspend fun peerStatus(): OwnedControlResponse? = transport.forwardControl(OwnedControlRequest(
                        request.operationId, "status", prepared.generation, candidateSha256 = candidate.candidateSha256,
                        topologySha256 = binding.topologySha256))
                    val first = peerStatus()
                    val firstAck = first?.ack(config.peerId, prepared.generation, candidate.candidateSha256, binding.topologySha256)
                    if (!isAccepting() || firstAck == null || first.accepting != true) return@withLock reply("pending", prepared)
                    val second = peerStatus()
                    val secondAck = second?.ack(config.peerId, prepared.generation, candidate.candidateSha256, binding.topologySha256)
                    if (!isAccepting() || secondAck?.processId != firstAck.processId || second?.accepting != true)
                        return@withLock reply("pending", prepared)
                    val resumed = authority.resume(prepared.generation, binding)
                    reply("ok", resumed).also { cache[request] = it }
                }
                "step" -> {
                    if (!ingress) return@withLock reply("forbidden", policy)
                    val cached = cache[request]
                    if (cached != null) {
                        // A replay is safe only while the disabled authority and both original processes remain current.
                        val saved = Json.decodeFromString<WireProof>(checkNotNull(cached.proof))
                        val localAck = localNode(candidate).status(saved.acknowledgedPolicy.generation)
                        val peerAck = peerNode(candidate, request.operationId).status(saved.acknowledgedPolicy.generation)
                        if (policy == saved.disabledPolicy && localAck != null && peerAck != null &&
                            listOf(localAck, peerAck).all { it.quiesced } &&
                            setOf(localAck.nodeId.toString() to localAck.processId.toString(), peerAck.nodeId.toString() to peerAck.processId.toString()) ==
                            saved.nodes.map { it.nodeId to it.processId }.toSet() && rooms.durableSnapshot().emptyForControl()
                        ) return@withLock cached
                        return@withLock reply("unavailable", policy)
                    }
                    val cluster = OwnedClusterDrain(binding, candidate.candidateSha256, localNode(candidate),
                        peerNode(candidate, request.operationId), authority::observe, { rooms.durableSnapshot() },
                        { current, next -> authority.advanceDrain(current, binding, next) },
                        { current -> authority.verifyDisabledZero(current, binding) })
                    when (val result = cluster.step(request.generation)) {
                        is OwnedDrainOutcome.Completed -> {
                            val wire = WireProof.from(result.proof, candidate)
                            val response = reply("completed", result.proof.disabledPolicy).copy(proof = Json.encodeToString(wire))
                            if (cache.size >= 128) cache.remove(cache.keys.first())
                            cache[request] = response
                            response
                        }
                        is OwnedDrainOutcome.Incomplete -> reply("pending", result.policy)
                        is OwnedDrainOutcome.Unavailable -> reply("unavailable", result.policy)
                    }
                }
                else -> reply("forbidden", policy)
            }
        } catch (failure: CancellationException) { throw failure }
        catch (_: SQLException) { reply("unavailable") }
        catch (_: OwnedAdmissionRejected) { reply("stale") }
        catch (_: IllegalArgumentException) { reply("unavailable") }
        catch (_: java.io.IOException) { reply("unavailable") }
    }

    private suspend fun status(request: OwnedControlRequest, candidate: OwnedCandidateIdentity,
        policy: OwnedAdmissionPolicy): OwnedControlResponse {
        if (policy.generation != request.generation &&
            !(policy.state == OwnedAdmissionState.DISABLED && quiescedGeneration == request.generation))
            return OwnedControlResponse(request.operationId, request.operation, request.generation, "stale", policy = policy)
        val current = rooms.durableSnapshot()
        val again = authority.observe()
        if (again != policy) return OwnedControlResponse(request.operationId, request.operation, request.generation, "stale", policy = again)
        return OwnedControlResponse(request.operationId, request.operation, request.generation, "ok", config.nodeId.toString(),
            process.toString(), candidate.candidateSha256, binding.topologySha256,
            isDrained() && quiescedGeneration == request.generation, policy,
            current.remainingOpenRooms, current.pendingSettlements, current.remainingOwnedLeases, accepting = isAccepting())
    }

    private fun localNode(candidate: OwnedCandidateIdentity): OwnedDrainNode = object : OwnedDrainNode {
        override val nodeId = config.nodeId
        override suspend fun status(generation: Long): OwnedNodeDrainAck? {
            val policy = authority.observe() ?: return null
            val request = OwnedControlRequest(UUID.randomUUID().toString(), "status", generation)
            return status(request, candidate, policy).ack(nodeId, generation, candidate.candidateSha256, binding.topologySha256)
        }
        override suspend fun closeWaiting(generation: Long): Boolean { closeWaiting.invoke(generation); return true }
        override suspend fun quiesce(generation: Long): OwnedNodeDrainAck? {
            val policy = authority.observe() ?: return null
            if (policy.generation != generation || policy.state !in setOf(OwnedAdmissionState.QUIESCING, OwnedAdmissionState.DISABLED)) return null
            authority.fenceQuiesce(policy, binding, closeGate)
            if (!beginDrain()) return null
            quiescedGeneration = generation
            return status(generation)
        }
    }

    private fun peerNode(candidate: OwnedCandidateIdentity, operationId: String): OwnedDrainNode = object : OwnedDrainNode {
        override val nodeId = config.peerId
        private var instance: UUID? = null
        private suspend fun send(operation: String, generation: Long, process: UUID? = null): OwnedControlResponse? =
            transport.forwardControl(OwnedControlRequest(operationId, operation, generation, process?.toString(),
                candidate.candidateSha256, binding.topologySha256))
        override suspend fun status(generation: Long): OwnedNodeDrainAck? {
            val ack = send("status", generation)?.ack(nodeId, generation, candidate.candidateSha256, binding.topologySha256)
            instance = ack?.processId
            return ack
        }
        override suspend fun closeWaiting(generation: Long): Boolean =
            instance?.let { send("close_waiting", generation, it)?.outcome == "ok" } == true
        override suspend fun quiesce(generation: Long): OwnedNodeDrainAck? {
            // A fresh process observation is required even on resumed QUIESCING.
            val current = status(generation) ?: return null
            return send("quiesce", generation, current.processId)?.ack(nodeId, generation, candidate.candidateSha256, binding.topologySha256)
        }
    }
}

private fun OwnedControlResponse.ack(node: UUID, generation: Long, candidate: String, topology: String): OwnedNodeDrainAck? {
    if (outcome != "ok" || nodeId != node.toString() || candidateSha256 != candidate || topologySha256 != topology ||
        this.generation != generation || quiesced == null || openRooms == null || pendingSettlements == null || liveLeases == null ||
        openRooms < 0 || pendingSettlements < 0 || liveLeases < 0) return null
    val instance = processId?.let(::parsePilotUuid) ?: return null
    return OwnedNodeDrainAck(node, instance, generation, candidate, topology, quiesced,
        RoomOwnershipDurableSnapshot(liveLeases, openRooms, pendingSettlements))
}

private fun RoomOwnershipDurableSnapshot.emptyForControl() =
    remainingOwnedLeases == 0 && remainingOpenRooms == 0 && pendingSettlements == 0

@Serializable
private data class WireNode(val nodeId: String, val processId: String, val generation: Long)
@Serializable
private data class WireProof(
    val serverJarSha256: String, val classpathSha256: String, val candidateSha256: String, val topologySha256: String,
    val mode: OwnedProofMode, val acknowledgedPolicy: OwnedAdmissionPolicy, val disabledPolicy: OwnedAdmissionPolicy,
    val nodes: List<WireNode>, val openRooms: Int, val pendingSettlements: Int, val liveLeases: Int
) {
    companion object {
        fun from(proof: OwnedRollbackProof, identity: OwnedCandidateIdentity) = WireProof(identity.serverJarSha256,
            identity.classpathSha256, proof.candidateSha256, proof.topologySha256, proof.mode, proof.acknowledgedPolicy, proof.disabledPolicy,
            proof.nodes.map { WireNode(it.nodeId.toString(), it.processId.toString(), it.generation) },
            proof.snapshot.remainingOpenRooms, proof.snapshot.pendingSettlements, proof.snapshot.remainingOwnedLeases)
    }
}
