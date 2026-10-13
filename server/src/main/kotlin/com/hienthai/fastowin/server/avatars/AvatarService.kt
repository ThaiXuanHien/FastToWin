package com.hienthai.fastowin.server.avatars

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

class AvatarService(
    private val repository: AvatarRepository,
    private val objectStore: AvatarObjectStore? = null,
    private val objectWrites: Boolean = false,
    private val normalizer: AvatarImageNormalizer = AvatarImageNormalizer()
) {
    init { require(!objectWrites || objectStore != null) { "Object avatar writes require configured object storage." } }

    suspend fun upload(playerId: String, base64: String): Boolean {
        if (!isAvatarPlayerId(playerId)) return false
        return try {
            val image = withContext(Dispatchers.Default) { normalizer.normalize(base64) } ?: return false
            val previous = repository.current(playerId) ?: return false
            if (!objectWrites) {
                repository.writeLegacy(playerId, previous.revision, Base64.getEncoder().encodeToString(image.bytes))
            } else {
                val key = "avatars/${UUID.fromString(playerId)}/${UUID.randomUUID()}.jpg"
                if (!repository.beginIntent(playerId, previous.revision, key)) return false
                // The durable intent already exists if this call fails or the process stops.
                objectStore!!.put(key, image.bytes, image.contentType)
                repository.commit(playerId, previous.revision, AvatarMetadata(
                    key, image.contentType, image.bytes.size, avatarSha256(image.bytes), image.width, image.height))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun read(playerId: String): AvatarReadResult {
        if (!isAvatarPlayerId(playerId)) return AvatarReadResult.NotFound
        return try {
            val snapshot = repository.current(playerId) ?: return AvatarReadResult.NotFound
            val metadata = snapshot.metadata
            if (metadata == null) {
                val legacy = snapshot.legacyBase64 ?: return AvatarReadResult.NotFound
                val image = withContext(Dispatchers.Default) { normalizer.normalizeLegacy(legacy) }
                    ?: return AvatarReadResult.Unavailable
                AvatarReadResult.Found(image.bytes, image.contentType)
            } else {
                val storage = objectStore ?: return AvatarReadResult.Unavailable
                val bytes = storage.read(metadata.key, AvatarImageNormalizer.MAX_BYTES) ?: return AvatarReadResult.Unavailable
                if (bytes.size != metadata.byteSize || bytes.size > AvatarImageNormalizer.MAX_BYTES ||
                    metadata.contentType != "image/jpeg" || avatarSha256(bytes) != metadata.sha256) {
                    AvatarReadResult.Unavailable
                } else AvatarReadResult.Found(bytes, metadata.contentType)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AvatarReadResult.Unavailable
        }
    }

    suspend fun cleanupBatch() {
        val storage = objectStore ?: return
        for (item in repository.claimCleanup(25)) {
            try {
                storage.delete(item.key)
                repository.completeCleanup(item.key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed DB retry update leaves the existing lease to be retried after restart.
                try { repository.retryCleanup(item.key) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Durable work is retained. */ }
            }
        }
    }
}

internal fun avatarSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun isAvatarPlayerId(value: String): Boolean = try {
    UUID.fromString(value).toString().equals(value, ignoreCase = true)
} catch (_: IllegalArgumentException) { false }
