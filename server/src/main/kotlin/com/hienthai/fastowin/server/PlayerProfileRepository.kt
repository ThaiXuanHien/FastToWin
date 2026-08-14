package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.MatchDetailSnapshot

interface PlayerProfileRepository {
    suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot?
    suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?): Boolean
    suspend fun findMatchDetail(playerId: String, matchId: String): MatchDetailSnapshot? = null
    suspend fun equipCosmetics(playerId: String, frameId: String, titleId: String): Boolean = false
}

object NoOpPlayerProfileRepository : PlayerProfileRepository {
    override suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot? = null
    override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?): Boolean = false
}
