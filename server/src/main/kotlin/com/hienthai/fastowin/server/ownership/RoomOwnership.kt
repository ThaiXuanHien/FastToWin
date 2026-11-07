package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.StoredActiveRoom
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.time.Instant
import java.util.UUID

internal data class OwnerFence(val roomId: UUID, val instanceId: UUID, val epoch: Long)

internal data class SocketFence(val playerId: UUID, val generation: Long)

internal data class OwnedRoomState(
    val fence: OwnerFence,
    val revision: Long,
    val snapshot: StoredActiveRoom,
    val recoveryDeadline: Instant?,
    val closed: Boolean = false
)

@Serializable
internal data class RoomOwnershipEvent(
    val recipientIds: Set<String>,
    val message: ServerMessage
) {
    constructor(recipients: Collection<UUID>, message: ServerMessage) :
        this(recipients.map(UUID::toString).toSet(), message)

    val recipients: Set<UUID> get() = recipientIds.map(UUID::fromString).toSet()
}

internal data class RoomEventRevision(
    val ownerEpoch: Long,
    val revision: Long,
    val events: List<RoomOwnershipEvent>
)

internal sealed interface RoomEventBatch {
    data class Contiguous(val revisions: List<RoomEventRevision>) : RoomEventBatch
    data object Gap : RoomEventBatch
}

internal sealed interface RoomMutation {
    data class Change(
        val snapshot: StoredActiveRoom,
        val events: List<RoomOwnershipEvent> = emptyList(),
        val close: Boolean = false
    ) : RoomMutation

    data class Replay(val message: ServerMessage) : RoomMutation
    data class Reject(val reason: String) : RoomMutation
    data object Unchanged : RoomMutation
}

internal sealed interface FencedResult {
    data class Committed(val state: OwnedRoomState) : FencedResult
    data class Unchanged(val state: OwnedRoomState) : FencedResult
    data class Replay(val message: ServerMessage) : FencedResult
    data class Rejected(val reason: String) : FencedResult
    data object Stale : FencedResult
    data object TemporaryFailure : FencedResult
}

internal interface RoomOwnershipRepository {
    suspend fun claim(roomId: UUID, instanceId: UUID): OwnedRoomState?
    suspend fun renew(fence: OwnerFence): Boolean
    suspend fun release(fence: OwnerFence): Boolean
    suspend fun lookupRoom(playerId: UUID): UUID?
    suspend fun read(roomId: UUID): OwnedRoomState?
    suspend fun bindSocket(playerId: UUID): SocketFence
    suspend fun purgeExpiredClosedRooms(limit: Int): Int
    suspend fun readEvents(roomId: UUID, afterRevision: Long, limit: Int): RoomEventBatch

    suspend fun createRoom(
        instanceId: UUID,
        socket: SocketFence,
        initialSnapshot: StoredActiveRoom,
        events: List<RoomOwnershipEvent> = emptyList()
    ): FencedResult

    suspend fun withFencedRoom(
        fence: OwnerFence,
        expectedRevision: Long,
        socket: SocketFence?,
        change: (Connection, OwnedRoomState) -> RoomMutation
    ): FencedResult
}
