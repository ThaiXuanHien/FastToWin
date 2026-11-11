package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolGameMode
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Retain this identity across every uncertain attempt. Never log the password-bearing command. */
internal class OwnedCreateRequest(
    val operationId: UUID,
    val roomId: UUID,
    val socket: SocketFence,
    val routeKey: String,
    val destinationNodeId: UUID,
    val policy: OwnedAdmissionPolicy,
    val command: ClientMessage.CreateRoom
) {
    fun copy(operationId: UUID = this.operationId, roomId: UUID = this.roomId, socket: SocketFence = this.socket,
        routeKey: String = this.routeKey, destinationNodeId: UUID = this.destinationNodeId,
        policy: OwnedAdmissionPolicy = this.policy, command: ClientMessage.CreateRoom = this.command
    ) = OwnedCreateRequest(operationId, roomId, socket, routeKey, destinationNodeId, policy, command)

    override fun equals(other: Any?): Boolean = other is OwnedCreateRequest && operationId == other.operationId &&
        roomId == other.roomId && socket == other.socket && routeKey == other.routeKey &&
        destinationNodeId == other.destinationNodeId && policy == other.policy && command == other.command
    override fun hashCode(): Int = operationId.hashCode()
    override fun toString(): String = "OwnedCreateRequest(redacted)"

    fun isSupported(): Boolean = routeKey == socket.playerId.toString() && socket.generation > 0 &&
        policy.generation > 0 && policy.state == OwnedAdmissionState.ACTIVE &&
        policy.topologySha256?.matches(CREATE_DIGEST) == true && policy.policySha256?.matches(CREATE_DIGEST) == true &&
        command.gameMode == ProtocolGameMode.ORDER && command.matchType == MatchType.CASUAL &&
        command.password.isNotBlank() && command.roomName.trim().isNotEmpty()

    /** Versioned length-prefix encoding includes only nonsecret identity, including the raw name. */
    fun nonsecretSha256(): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            listOf("fasttowin/owned-create-operation/v1", operationId.toString(), roomId.toString(),
                socket.playerId.toString(), socket.generation.toString(), routeKey, destinationNodeId.toString(),
                policy.generation.toString(), policy.state.name, policy.topologySha256.orEmpty(),
                policy.policySha256.orEmpty(), command.roomName, command.gameMode.name, command.matchType.name
            ).forEach { field -> val utf8 = field.toByteArray(Charsets.UTF_8); out.writeInt(utf8.size); out.write(utf8) }
        }
        return createSha256(bytes.toByteArray())
    }
}

/** Historical durable evidence only: never a lease fence, delivery cursor or public game snapshot. */
internal data class OwnedCreateProof(
    val operationId: UUID, val roomId: UUID, val destinationNodeId: UUID,
    val createdInstanceId: UUID, val createdEpoch: Long, val createdRevision: Long
) {
    fun matches(request: OwnedCreateRequest): Boolean = operationId == request.operationId && roomId == request.roomId &&
        destinationNodeId == request.destinationNodeId && createdEpoch > 0 && createdRevision > 0
}

internal sealed interface OwnedCreateResult {
    data class Created(val proof: OwnedCreateProof, val replayed: Boolean) : OwnedCreateResult
    data class Closed(val proof: OwnedCreateProof, val closedEpoch: Long, val closedRevision: Long) : OwnedCreateResult
    data class AttemptRejected(val reason: String) : OwnedCreateResult
    data class Unresolved(val reason: String) : OwnedCreateResult
}

internal sealed interface OwnedCreatePreparation {
    data class Ready(val request: OwnedCreateRequest) : OwnedCreatePreparation
    data class Existing(val roomId: UUID) : OwnedCreatePreparation
    data class Rejected(val reason: String) : OwnedCreatePreparation
}

@Serializable
internal class OwnedCreateWireRequest(
    val operationId: String, val roomId: String, val playerId: String, val generation: Long,
    val routeKey: String, val destinationNodeId: String, val policy: OwnedAdmissionPolicy,
    val command: ClientMessage.CreateRoom, val version: Int = 1
) {
    override fun toString(): String = "OwnedCreateWireRequest(redacted)"

    fun toRequest(): OwnedCreateRequest? {
        if (version != 1) return null
        return OwnedCreateRequest(canonicalCreateUuid(operationId) ?: return null,
            canonicalCreateUuid(roomId) ?: return null,
            SocketFence(canonicalCreateUuid(playerId) ?: return null, generation), routeKey,
            canonicalCreateUuid(destinationNodeId) ?: return null, policy, command).takeIf { it.isSupported() }
    }

    companion object {
        fun from(request: OwnedCreateRequest) = OwnedCreateWireRequest(request.operationId.toString(), request.roomId.toString(),
            request.socket.playerId.toString(), request.socket.generation, request.routeKey,
            request.destinationNodeId.toString(), request.policy, request.command)
    }
}

internal fun canonicalCreateUuid(value: String): UUID? = parsePilotUuid(value)?.takeIf { it.toString() == value }
internal fun createSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it) }
private val CREATE_DIGEST = Regex("[0-9a-f]{64}")
