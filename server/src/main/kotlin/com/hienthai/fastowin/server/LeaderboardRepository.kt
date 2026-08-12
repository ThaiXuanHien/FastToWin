package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.LeaderboardSnapshot

fun interface LeaderboardRepository {
    suspend fun load(currentPlayerId: String, limit: Int): LeaderboardSnapshot
}

object NoOpLeaderboardRepository : LeaderboardRepository {
    override suspend fun load(currentPlayerId: String, limit: Int) = LeaderboardSnapshot()
}
