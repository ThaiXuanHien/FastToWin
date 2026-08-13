package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.PlayerProfileSnapshot

interface PlayerProfileRepository {
    suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot?
    suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?): Boolean
}

object NoOpPlayerProfileRepository : PlayerProfileRepository {
    override suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot? = null
    override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?): Boolean = false
}
