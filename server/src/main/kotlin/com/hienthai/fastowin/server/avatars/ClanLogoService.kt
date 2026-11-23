package com.hienthai.fastowin.server.avatars

import com.hienthai.fastowin.protocol.CLAN_LOGO_DAILY_CHANGE_LIMIT
import com.hienthai.fastowin.protocol.CLAN_LOGO_MAX_EDGE

interface ClanLogoChangeLog {
    suspend fun changesSince(clanId: String, sinceMillis: Long): Int
    suspend fun recordChange(clanId: String, atMillis: Long)
}

enum class ClanLogoUploadStatus { UPDATED, DAILY_LIMIT, INVALID_IMAGE }

/** Uploaded clan logos: centered square, JPEG, at most 256 px and 45 KiB, five uploads per day. */
class ClanLogoService(
    private val images: AvatarService,
    private val changes: ClanLogoChangeLog,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun upload(clanId: String, base64: String): ClanLogoUploadStatus {
        val now = nowMillis()
        if (changes.changesSince(clanId, now - DAY_MILLIS) >= CLAN_LOGO_DAILY_CHANGE_LIMIT) return ClanLogoUploadStatus.DAILY_LIMIT
        if (!images.upload(clanId, base64)) return ClanLogoUploadStatus.INVALID_IMAGE
        runCatching { changes.recordChange(clanId, now) }
        return ClanLogoUploadStatus.UPDATED
    }

    suspend fun remove(clanId: String): Boolean = images.remove(clanId)
    suspend fun read(clanId: String): AvatarReadResult = images.read(clanId)
    suspend fun currentRevision(clanId: String): String? = images.currentRevision(clanId)

    companion object {
        private const val DAY_MILLIS = 86_400_000L
        fun normalizer() = AvatarImageNormalizer(maxEdge = CLAN_LOGO_MAX_EDGE, squareCrop = true)
        const val KEY_PREFIX = "clan-logos"
    }
}

/** Memory/dev engines keep uploaded logos as legacy base64 beside the clan repository. */
fun inMemoryClanLogoService(
    clans: com.hienthai.fastowin.server.ClanRepository,
    nowMillis: () -> Long = System::currentTimeMillis
): ClanLogoService {
    val repository = InMemoryClanLogoRepository { clans.getClanById(it) != null }
    return ClanLogoService(AvatarService(repository, normalizer = ClanLogoService.normalizer(),
        keyPrefix = ClanLogoService.KEY_PREFIX), repository, nowMillis)
}
