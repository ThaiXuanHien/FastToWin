package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.server.MatchWriteOutcome
import com.hienthai.fastowin.server.PlayQuotaConsumptionStatus
import com.hienthai.fastowin.server.PostgresMatchResultRepository
import com.hienthai.fastowin.server.PostgresPlayQuotaRepository
import com.hienthai.fastowin.server.StoredActiveRoom
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

internal class OwnedMatchEconomy(
    private val ownership: RoomOwnershipRepository,
    private val quota: PostgresPlayQuotaRepository,
    private val matches: PostgresMatchResultRepository
) {
    private val conflicts = AtomicLong()
    val integrityConflicts: Long get() = conflicts.get()

    suspend fun start(
        fence: OwnerFence,
        revision: Long,
        socket: SocketFence,
        candidate: StoredActiveRoom,
        events: List<RoomOwnershipEvent> = emptyList()
    ): FencedResult = ownership.withFencedRoom(fence, revision, socket) { connection, state ->
        val room = state.snapshot
        val participants = room.accountParticipants()
        val startedAt = candidate.startedAtEpochMillis
        if (participants.size != 2 || socket.playerId.toString() !in participants ||
            room.roomId != candidate.roomId || room.matchId != candidate.matchId ||
            room.host.playerId != candidate.host.playerId || room.guest?.playerId != candidate.guest?.playerId ||
            participants != candidate.accountParticipants() ||
            room.gameMode != candidate.gameMode || room.matchType != candidate.matchType ||
            candidate.phase != RoomPhase.PLAYING || candidate.readyPlayerIds.isNotEmpty() ||
            startedAt == null || candidate.pendingCompletedMatch != null || candidate.finishedAtEpochMillis != null
        ) return@withFencedRoom RoomMutation.Reject("INVALID_MATCH_START")

        // Retrying a start for the same match must leave the durable board and quota intact.
        if (room.phase == RoomPhase.PLAYING) return@withFencedRoom RoomMutation.Unchanged
        // The caller is the last player setting ready; the start transition clears both ready flags.
        if (room.phase != RoomPhase.WAITING || room.startedAtEpochMillis != null ||
            (participants - socket.playerId.toString()).any { it !in room.readyPlayerIds }
        ) return@withFencedRoom RoomMutation.Reject("INVALID_MATCH_START")

        val consumed = quota.reserveAndFinalizeMatch(connection, participants, room.matchId, startedAt)
        if (consumed.status == PlayQuotaConsumptionStatus.EXHAUSTED) {
            RoomMutation.Reject("PLAY_QUOTA_EXHAUSTED")
        } else {
            RoomMutation.Change(candidate, events)
        }
    }

    suspend fun settle(fence: OwnerFence, revision: Long): FencedResult =
        ownership.withFencedRoom(fence, revision, socket = null) { connection, state ->
            val room = state.snapshot
            if (room.phase != RoomPhase.FINISHED) return@withFencedRoom RoomMutation.Reject("MATCH_NOT_FINISHED")
            val match = room.pendingCompletedMatch
            if (match == null) {
                return@withFencedRoom if (matches.hasOwnedSettlement(connection, fence.roomId, UUID.fromString(room.matchId))) {
                    RoomMutation.Unchanged
                } else {
                    RoomMutation.Reject("MATCH_RESULT_MISSING")
                }
            }
            if (match.matchId != room.matchId || match.roomId != room.roomId ||
                match.gameMode != room.gameMode || match.matchType != room.matchType ||
                match.startedAtMillis != room.startedAtEpochMillis || match.endedAtMillis != room.finishedAtEpochMillis ||
                match.players.size != 2 || match.players.map { it.playerId }.toSet() != room.accountParticipants()
            ) return@withFencedRoom RoomMutation.Reject("INVALID_MATCH_RESULT")

            when (matches.saveMatch(connection, match)) {
                MatchWriteOutcome.CONFLICT -> {
                    conflicts.incrementAndGet()
                    RoomMutation.Reject("MATCH_RESULT_CONFLICT")
                }
                MatchWriteOutcome.INSERTED, MatchWriteOutcome.ALREADY_SAVED ->
                    RoomMutation.Change(room.copy(pendingCompletedMatch = null))
            }
        }

    private fun StoredActiveRoom.accountParticipants(): Set<String> =
        listOfNotNull(host, guest).filter { it.isAccount }.map { it.playerId }.toSet()
}
