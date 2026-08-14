package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ProtocolGameMode
import kotlinx.serialization.Serializable

@Serializable
enum class MatchOutcome {
    WIN,
    LOSS,
    DRAW
}

@Serializable
data class CompletedMatchPlayer(
    val playerId: String,
    val displayName: String,
    val score: Int,
    val outcome: MatchOutcome
)

@Serializable
enum class SelectionResult {
    ACCEPTED,
    REJECTED
}

@Serializable
data class MatchSelectionEvent(
    val playerId: String,
    val requestId: String,
    val number: Int,
    val expectedNumber: Int,
    val result: SelectionResult,
    val occurredAtMillis: Long,
    val sequence: Int
)

data class CompletedMatch(
    val matchId: String,
    val roomName: String,
    val gameMode: ProtocolGameMode,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val winnerPlayerId: String?,
    val players: List<CompletedMatchPlayer>,
    val events: List<MatchSelectionEvent> = emptyList()
)

fun interface MatchResultRepository {
    suspend fun save(match: CompletedMatch)
}

object NoOpMatchResultRepository : MatchResultRepository {
    override suspend fun save(match: CompletedMatch) = Unit
}
