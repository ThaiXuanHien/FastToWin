package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.GAME_NUMBER_COUNT
import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.MatchRewardEventSnapshot
import com.hienthai.fastowin.protocol.PlayerSnapshot
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.CompletedMatch
import com.hienthai.fastowin.server.CompletedMatchPlayer
import com.hienthai.fastowin.server.MatchOutcome
import com.hienthai.fastowin.server.MatchSelectionEvent
import com.hienthai.fastowin.server.RewardEconomy
import com.hienthai.fastowin.server.SelectionResult
import com.hienthai.fastowin.server.StoredActiveRoom
import com.hienthai.fastowin.server.roomSelectionMetrics

internal fun StoredActiveRoom.players() = listOfNotNull(host, guest)

internal fun StoredActiveRoom.activePlayers(): Set<String> =
    setOfNotNull(host.playerId, guest?.playerId) - departedPlayerIds

internal fun StoredActiveRoom.startClassic(now: Long, rewards: MatchRewardEventSnapshot): StoredActiveRoom {
    val ids = activePlayers()
    return copy(
        phase = RoomPhase.PLAYING,
        numbers = (1..GAME_NUMBER_COUNT).shuffled(),
        targetOrder = (1..GAME_NUMBER_COUNT).toList(),
        targetIndexes = ids.associateWith { 0 },
        scores = ids.associateWith { 0 },
        sequence = sequence + 1,
        readyPlayerIds = emptySet(),
        startedAtEpochMillis = now,
        eventRewards = rewards
    )
}

internal fun StoredActiveRoom.recordClassicSelection(event: MatchSelectionEvent): StoredActiveRoom {
    val updated = copy(sequence = sequence + 1, selectionEvents = selectionEvents + event)
    if (event.result == SelectionResult.REJECTED) {
        return updated.copy(combos = combos + (event.playerId to 0))
    }
    val selected = selectedNumbers + event.number
    val next = updated.copy(
        selectedNumbers = selected,
        selectedNumbersByPlayer = activePlayers().associateWith { selected },
        targetIndexes = activePlayers().associateWith { selected.size },
        currentTarget = event.number + 1,
        scores = scores + (event.playerId to (scores.getValue(event.playerId) + SCORE_PER_NUMBER)),
        combos = combos + (event.playerId to ((combos[event.playerId] ?: 0) + 1))
    )
    return if (selected.size == GAME_NUMBER_COUNT) {
        next.finishClassic(event.occurredAtMillis, lastSelector = event.playerId)
    } else next
}

internal fun StoredActiveRoom.finishClassic(
    now: Long,
    forcedWinner: String? = null,
    intentionalLeaver: String? = null,
    disconnected: String? = null,
    lastSelector: String? = null
): StoredActiveRoom {
    val finished = copy(
        phase = RoomPhase.FINISHED,
        finishedAtEpochMillis = now,
        forcedWinnerId = forcedWinner,
        intentionalLeavePlayerIds = setOfNotNull(intentionalLeaver),
        finishedPlayerIds = setOfNotNull(intentionalLeaver, disconnected, lastSelector),
        resultQueued = true
    )
    val winner = finished.winner()
    return finished.copy(
        pendingCompletedMatch = CompletedMatch(
            matchId, roomName, gameMode, checkNotNull(startedAtEpochMillis), now, winner,
            players().map { player ->
                val id = player.playerId
                CompletedMatchPlayer(id, player.displayName, scores.getValue(id), outcome(winner, id), id == intentionalLeaver)
            },
            selectionEvents, matchType, eventRewards, roomId
        )
    )
}

internal fun StoredActiveRoom.classicSnapshot(): GameSnapshot {
    val metrics = roomSelectionMetrics(players().map { it.playerId }.toSet(), startedAtEpochMillis, selectionEvents, false)
    val winner = winner().takeIf { phase == RoomPhase.FINISHED }
    return GameSnapshot(
        roomId = roomId,
        matchId = matchId,
        roomName = roomName,
        hostId = host.playerId,
        gameMode = gameMode,
        matchType = matchType,
        phase = phase,
        players = players().map { player ->
            val id = player.playerId
            val selection = metrics.getValue(id)
            val fastest = selection.fastestSegment
            val slowest = selection.slowestSegment
            PlayerSnapshot(
                id = id,
                name = player.displayName,
                score = scores[id] ?: 0,
                isReady = id in readyPlayerIds,
                correctSelections = selection.correct,
                wrongSelections = selection.wrong,
                averageReactionMillis = selection.averageReactionMillis,
                currentTarget = currentTarget,
                selectedNumbers = selectedNumbersByPlayer[id].orEmpty(),
                combo = combos[id] ?: 0,
                isFinished = id in finishedPlayerIds,
                fastestSegmentStart = fastest?.start ?: 0,
                fastestSegmentEnd = fastest?.end ?: 0,
                fastestSegmentAverageMillis = fastest?.averageMillis ?: 0,
                slowestSegmentStart = slowest?.start ?: 0,
                slowestSegmentEnd = slowest?.end ?: 0,
                slowestSegmentAverageMillis = slowest?.averageMillis ?: 0,
                avatarId = player.avatarId,
                frameId = player.frameId
            )
        },
        numbers = numbers,
        selectedNumbers = selectedNumbers,
        currentTarget = currentTarget,
        sequence = sequence,
        startedAtEpochMillis = startedAtEpochMillis,
        finishedAtEpochMillis = finishedAtEpochMillis,
        winnerPlayerId = winner,
        eventRewards = eventRewards.takeIf { startedAtEpochMillis != null },
        rewardBreakdowns = if (phase == RoomPhase.FINISHED) {
            players().associate { player ->
                val id = player.playerId
                id to RewardEconomy.matchRewardBreakdown(matchType, outcome(winner, id), id in intentionalLeavePlayerIds, eventRewards)
            }
        } else emptyMap()
    )
}

internal fun StoredActiveRoom.publicState(): ServerMessage = when (phase) {
    RoomPhase.FINISHED -> ServerMessage.GameFinished(classicSnapshot())
    RoomPhase.WAITING -> ServerMessage.RoomUpdated(classicSnapshot())
    RoomPhase.PLAYING -> ServerMessage.GameStateUpdated(
        classicSnapshot(), acceptedNumber = 0, selectedByPlayerId = "", selectionAccepted = false
    )
}

private fun StoredActiveRoom.winner(): String? =
    forcedWinnerId ?: scores.values.maxOrNull()?.let { high -> scores.filterValues { it == high }.keys.singleOrNull() }

private fun outcome(winner: String?, player: String): MatchOutcome = when (winner) {
    null -> MatchOutcome.DRAW
    player -> MatchOutcome.WIN
    else -> MatchOutcome.LOSS
}

private const val SCORE_PER_NUMBER = 10
