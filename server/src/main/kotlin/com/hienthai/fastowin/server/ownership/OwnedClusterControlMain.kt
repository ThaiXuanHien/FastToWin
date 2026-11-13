package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.UUID

/** Run inside worker B with its production environment and the same ordered runtime classpath. */
fun main(args: Array<String>) = runBlocking {
    val config = requireNotNull(RoomOwnershipRuntimeConfigLoader.fromEnvironment("prod"))
    require(config.fixtureId == null && config.runtimeRole == RoomOwnershipRuntimeRole.OWNED_WORKER) {
        "Control must run in the production worker environment"
    }
    val identity = OwnedCandidateIdentity.running()
    val binding = OwnedAdmissionBinding.from(config)
    val transport = OwnerPeerTransport(config.nodeId, config.peerId, config.peerUri, config.peerSecret)
    if (args.firstOrNull() == "await") {
        require(args.size == 4) { "Usage: await <operation-uuid> <generation> <deadline-millis>" }
        val operation = UUID.fromString(args[1]).also { require(it.toString() == args[1]) }
        val result = awaitClusterDrain(operation, args[2].toLong(), args[3].toLong(), config.peerId,
            identity, binding, send = transport::forwardControl)
        println(Json.encodeToString(OwnedAwaitReceipt.serializer(), result))
    } else {
        val request = parseControlArguments(args, identity, binding)
        val response = checkNotNull(transport.forwardControl(request)) { "Coordinator unavailable; reconcile using signed status" }
        println(Json.encodeToString(OwnedControlResponse.serializer(), response))
    }
}

internal fun parseControlArguments(args: Array<String>, identity: OwnedCandidateIdentity,
    binding: OwnedAdmissionBinding): OwnedControlRequest {
    require(args.size in 3..4) { "Usage: <status|stop_new|step|resume> <operation-uuid> <generation> [coordinator-process-uuid]" }
    val operation = args[0]
    require(operation in setOf("status", "stop_new", "step", "resume"))
    require(operation == "status" || args.size == 4) { "Mutations require the current coordinator process ID" }
    val operationId = UUID.fromString(args[1]).toString()
    require(operationId == args[1])
    val generation = requireNotNull(args[2].toLongOrNull()).also { require(it > 0) }
    val process = args.getOrNull(3)?.let { UUID.fromString(it).toString().also { value -> require(value == it) } }
    return OwnedControlRequest(operationId, operation, generation, process, identity.candidateSha256, binding.topologySha256)
}
