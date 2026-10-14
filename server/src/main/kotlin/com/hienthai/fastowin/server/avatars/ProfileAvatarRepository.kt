package com.hienthai.fastowin.server.avatars

import com.hienthai.fastowin.server.PlayerProfileRepository

/** Compatibility for memory/dev engines. Production always uses PostgresAvatarRepository. */
class ProfileAvatarRepository(private val profiles: PlayerProfileRepository) : AvatarRepository {
    override suspend fun current(playerId: String): AvatarSnapshot? =
        if (profiles.findByPlayerId(playerId) == null) null else AvatarSnapshot(0, profiles.getAvatarData(playerId), null)
    override suspend fun writeLegacy(playerId: String, expectedRevision: Long, base64: String) = profiles.updateAvatarData(playerId, base64)
    override suspend fun beginIntent(playerId: String, expectedRevision: Long, key: String) = false
    override suspend fun commit(playerId: String, expectedRevision: Long, metadata: AvatarMetadata) = false
    override suspend fun claimCleanup(limit: Int) = emptyList<AvatarCleanupItem>()
    override suspend fun completeCleanup(key: String) {}
    override suspend fun retryCleanup(key: String) {}
}
