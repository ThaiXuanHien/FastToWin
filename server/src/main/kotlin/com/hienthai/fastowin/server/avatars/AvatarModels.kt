package com.hienthai.fastowin.server.avatars

data class AvatarMetadata(val key: String, val contentType: String, val byteSize: Int, val sha256: String, val width: Int, val height: Int)
data class AvatarSnapshot(val revision: Long, val legacyBase64: String?, val metadata: AvatarMetadata?)
data class AvatarCleanupItem(val key: String)

sealed interface AvatarReadResult {
    data class Found(val bytes: ByteArray, val contentType: String = "image/jpeg") : AvatarReadResult
    data object NotFound : AvatarReadResult
    data object Unavailable : AvatarReadResult
}

interface AvatarObjectStore : AutoCloseable {
    suspend fun put(key: String, bytes: ByteArray, contentType: String)
    suspend fun read(key: String, maximumBytes: Int): ByteArray?
    suspend fun delete(key: String)
    override fun close() {}
}

interface AvatarRepository {
    suspend fun current(playerId: String): AvatarSnapshot?
    suspend fun writeLegacy(playerId: String, expectedRevision: Long, base64: String): Boolean
    suspend fun beginIntent(playerId: String, expectedRevision: Long, key: String): Boolean
    suspend fun commit(playerId: String, expectedRevision: Long, metadata: AvatarMetadata): Boolean
    suspend fun claimCleanup(limit: Int): List<AvatarCleanupItem>
    suspend fun completeCleanup(key: String)
    suspend fun retryCleanup(key: String)
}
