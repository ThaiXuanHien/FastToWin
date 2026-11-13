package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.UUID

/** Run inside worker B with its production environment and the same ordered runtime classpath. */
fun main(args: Array<String>) {
    val awaiting = args.firstOrNull() == "await"
    val operation = args.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: UUID(0, 0)
    val timeout = if (awaiting) args.getOrNull(3)?.toLongOrNull()?.takeIf { it in 1..3_600_000 } else 30_000L
    val lastStatus = java.util.concurrent.atomic.AtomicReference<OwnedControlResponse?>()
    val started = System.nanoTime()
    val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "owned-control-cli").apply { isDaemon = true }
    }
    val result = try {
        requireNotNull(timeout)
        val future = executor.submit<String> {
            runBlocking {
                val config = requireNotNull(RoomOwnershipRuntimeConfigLoader.fromEnvironment("prod"))
                require(config.fixtureId == null && config.runtimeRole == RoomOwnershipRuntimeRole.OWNED_WORKER)
                val identity = OwnedCandidateIdentity.running()
                val binding = OwnedAdmissionBinding.from(config)
                OwnerPeerTransport(config.nodeId, config.peerId, config.peerUri, config.peerSecret).use { transport ->
                    if (awaiting) {
                        require(args.size == 4 && operation.toString() == args[1])
                        val elapsed = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                        val remaining = (timeout - elapsed).coerceAtLeast(1)
                        val receipt = awaitClusterDrain(operation, args[2].toLong(), remaining, config.peerId, identity, binding,
                            onStatus = lastStatus::set, send = transport::forwardControl)
                        Json.encodeToString(OwnedAwaitReceipt.serializer(), receipt)
                    } else {
                        val request = parseControlArguments(args, identity, binding)
                        val response = requireNotNull(transport.forwardControl(request))
                        Json.encodeToString(OwnedControlResponse.serializer(), response)
                    }
                }
            }
        }
        try { future.get(timeout, java.util.concurrent.TimeUnit.MILLISECONDS) }
        catch (_: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            Json.encodeToString(OwnedAwaitReceipt.serializer(), OwnedAwaitReceipt(operation.toString(), "deadline", lastStatus.get()))
        }
    } catch (_: Exception) {
        Json.encodeToString(OwnedAwaitReceipt.serializer(), OwnedAwaitReceipt(operation.toString(), "unavailable", lastStatus.get()))
    } finally { executor.shutdownNow() }
    println(result)
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
