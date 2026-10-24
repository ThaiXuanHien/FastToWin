package com.hienthai.fastowin.server.avatars

import com.hienthai.fastowin.server.PlayerProfileRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Compatibility for memory/dev engines. Production always uses PostgresAvatarRepository. */
class ProfileAvatarRepository(private val profiles: PlayerProfileRepository) : AvatarRepository {
    private val mutex = Mutex()
    private val revisions = mutableMapOf<String, Long>()
    override suspend fun current(playerId: String): AvatarSnapshot? = mutex.withLock {
        if (profiles.findByPlayerId(playerId) == null) null else AvatarSnapshot(revisions[playerId] ?: 0, profiles.getAvatarData(playerId), null)
    }
    override suspend fun writeLegacy(playerId: String, expectedRevision: Long, base64: String): Boolean = mutex.withLock {
        if ((revisions[playerId] ?: 0) != expectedRevision || expectedRevision == Long.MAX_VALUE) return@withLock false
        if (!profiles.updateAvatarData(playerId, base64)) return@withLock false
        revisions[playerId] = expectedRevision + 1
        true
    }
    override suspend fun beginIntent(playerId: String, expectedRevision: Long, key: String) = false
    override suspend fun commit(playerId: String, expectedRevision: Long, metadata: AvatarMetadata) = false
    override suspend fun claimCleanup(limit: Int) = emptyList<AvatarCleanupItem>()
    override suspend fun completeCleanup(key: String) {}
    override suspend fun retryCleanup(key: String) {}
}
