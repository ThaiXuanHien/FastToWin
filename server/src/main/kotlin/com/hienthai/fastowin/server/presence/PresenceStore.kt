package com.hienthai.fastowin.server.presence

import com.hienthai.fastowin.protocol.FriendPresence
import java.util.UUID

interface PresenceStore : AutoCloseable {
    suspend fun renew(playerId: String, connectionId: UUID, presence: FriendPresence): Boolean
    suspend fun revoke(playerId: String, connectionId: UUID): Boolean
    suspend fun read(playerIds: Collection<String>): Map<String, FriendPresence>?
    fun renderMetrics(): String = ""
}
