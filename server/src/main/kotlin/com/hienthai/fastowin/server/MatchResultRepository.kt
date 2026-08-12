package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ProtocolGameMode

enum class MatchOutcome {
    WIN,
    LOSS,
    DRAW
}

data class CompletedMatchPlayer(
    val playerId: String,
    val displayName: String,
    val score: Int,
    val outcome: MatchOutcome
)

data class CompletedMatch(
    val matchId: String,
    val roomName: String,
    val gameMode: ProtocolGameMode,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val winnerPlayerId: String?,
    val players: List<CompletedMatchPlayer>
)

fun interface MatchResultRepository {
    suspend fun save(match: CompletedMatch)
}

object NoOpMatchResultRepository : MatchResultRepository {
    override suspend fun save(match: CompletedMatch) = Unit
}
