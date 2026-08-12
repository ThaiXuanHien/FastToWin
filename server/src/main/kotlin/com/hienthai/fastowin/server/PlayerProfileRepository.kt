package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.PlayerProfileSnapshot

fun interface PlayerProfileRepository {
    suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot?
}

object NoOpPlayerProfileRepository : PlayerProfileRepository {
    override suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot? = null
}
