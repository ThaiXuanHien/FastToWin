package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.network.AvatarFrameApiClient
import com.hienthai.fastowin.data.network.toAvatarImageUrl
import com.hienthai.fastowin.data.network.toHttpBaseUrl
import com.hienthai.fastowin.protocol.AvatarFrameDefinition
import com.hienthai.fastowin.protocol.MODERATED_AVATAR_ID
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
import com.hienthai.fastowin.protocol.DEFAULT_FEMALE_AVATAR_ID
import com.hienthai.fastowin.protocol.FRAME_CATALOG
import com.hienthai.fastowin.protocol.RankedTier
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.localization.localizedNetworkText
import com.hienthai.fastowin.localization.localizedRankedTierName
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalLocalization
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.avatar_player_blue
import com.hienthai.fastowin.resources.avatar_player_coral
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.CancellationException

@Composable
fun PlayerAvatar(
    displayName: String,
    avatarId: String?,
    userId: String? = null,
    frameId: String = "frame_default",
    size: Dp = 48.dp,
    imageUrl: String = "",
    previewBitmap: ImageBitmap? = null,
    modifier: Modifier = Modifier
) {
    val avatarServerUrl = LocalAvatarServerUrl.current
    val avatarRevision = LocalAvatarRevision.current
    val suppressedIds = LocalSuppressedAvatarIds.current
    val resolvedImageUrl = resolvePlayerAvatarImageUrl(avatarId, userId, imageUrl, avatarServerUrl, avatarRevision, suppressedIds)
    val isModerated = avatarId == MODERATED_AVATAR_ID || (previewBitmap == null && userId in suppressedIds)
    val frameName = localizedAvatarFrameName(frameId)
    val avatarDescription = localized(TextKey.AvatarOfPlayer, "player" to displayName)
    val frames = LocalAvatarFrames.current
    val frameUrl = frames?.let { resolveAvatarFrameImageUrl(avatarServerUrl, frameId, it) }.orEmpty()
    val avatarSize = size * 0.78f
    val illustratedAvatar = if (avatarId in coralAvatarIds) {
        Res.drawable.avatar_player_coral
    } else {
        Res.drawable.avatar_player_blue
    }

    Box(
        modifier = modifier
            .size(size)
            .testTag("avatar_frame:$frameId")
            .semantics(mergeDescendants = true) {
                contentDescription = avatarDescription
                stateDescription = frameName
        },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (isModerated) {
                Image(painterResource(Res.drawable.avatar_player_blue), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                NetworkImage(
                    url = resolvedImageUrl,
                    allowStaleCache = userId !in LocalInvalidatedAvatarIds.current,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = null,
                    fallback = {
                        Image(
                            painter = painterResource(illustratedAvatar),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
        }

        NetworkImage(frameUrl, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) {
            Box(Modifier.size(avatarSize).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        }
    }
}

/** A removed avatar must never resolve a cache key or a remote URL, including explicit callers. */
internal fun resolvePlayerAvatarImageUrl(avatarId: String?, userId: String?, imageUrl: String,
    serverUrl: String, revision: Long, suppressedIds: Set<String> = emptySet()): String {
    if (avatarId == MODERATED_AVATAR_ID || userId in suppressedIds) return ""
    return imageUrl.ifBlank {
        userId?.takeIf { it.isNotBlank() && serverUrl.isNotBlank() }
            ?.let { serverUrl.toAvatarImageUrl(it, revision) }.orEmpty()
    }
}

private val LocalAvatarServerUrl = staticCompositionLocalOf { "" }
private val LocalSuppressedAvatarIds = staticCompositionLocalOf { emptySet<String>() }
private val LocalInvalidatedAvatarIds = staticCompositionLocalOf { emptySet<String>() }
private val LocalAvatarRevision = staticCompositionLocalOf { 0L }
private val LocalAvatarFrames = staticCompositionLocalOf<Map<String, AvatarFrameDefinition>?> { null }

@Composable
fun AvatarImageProvider(
    serverUrl: String,
    revision: Long,
    suppressedIds: Set<String> = emptySet(),
    invalidatedIds: Set<String> = emptySet(),
    connected: Boolean = true,
    collectionOpen: Boolean = false,
    content: @Composable () -> Unit
) {
    val client = remember(serverUrl) { serverUrl.takeIf(String::isNotBlank)?.let(::AvatarFrameApiClient) }
    var frames by remember(client) { mutableStateOf<Map<String, AvatarFrameDefinition>?>(null) }
    DisposableEffect(client) { onDispose { client?.close() } }
    LaunchedEffect(client, connected, collectionOpen) {
        if (!connected || client == null) return@LaunchedEffect
        try {
            client.load()?.let { catalog -> frames = catalog.frames.associateBy { it.id } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep displaying the last good catalog during a network failure.
        }
    }
    CompositionLocalProvider(
        LocalAvatarServerUrl provides serverUrl,
        LocalAvatarRevision provides revision,
        LocalSuppressedAvatarIds provides suppressedIds,
        LocalInvalidatedAvatarIds provides invalidatedIds,
        LocalAvatarFrames provides frames,
        content = content
    )
}

internal fun resolveAvatarFrameImageUrl(
    serverUrl: String,
    frameId: String,
    frames: Map<String, AvatarFrameDefinition>
): String {
    if (serverUrl.isBlank()) return ""
    val tierFrameId = seasonalFrameTier(frameId)?.let { "frame_${it.name.lowercase()}" }
    val path = (frames[frameId] ?: tierFrameId?.let(frames::get))?.imagePath ?: return ""
    return if (avatarFrameImagePath.matches(path)) "${serverUrl.toHttpBaseUrl()}$path" else ""
}

private val avatarFrameImagePath = Regex("/api/avatar-frames/[a-z0-9_]+/image(?:\\?v=[A-Za-z0-9_-]+)?")

@Composable
internal fun catalogAvatarFrameName(frameId: String): String? = LocalAvatarFrames.current?.get(frameId)?.let { definition ->
    if (LocalLocalization.current.language == AppLanguage.VIETNAMESE) definition.nameVi else definition.nameEn
}

@Composable
private fun localizedAvatarFrameName(frameId: String): String {
    catalogAvatarFrameName(frameId)?.let { return it }
    seasonalFrameTier(frameId)?.let {
        return localized(TextKey.SeasonalFrame, "tier" to localizedRankedTierName(it))
    }
    FRAME_CATALOG.firstOrNull { it.id == frameId }?.let { definition ->
        return localizedNetworkText(definition.nameKey, emptyMap(), definition.fallbackName)
    }
    return localized(when (frameId) {
        "frame_bronze" -> TextKey.BronzeFrame
        "frame_silver" -> TextKey.SilverFrame
        "frame_gold" -> TextKey.GoldFrame
        "frame_perfect" -> TextKey.PerfectFrame
        "frame_persistent" -> TextKey.PersistentFrameName
        else -> TextKey.BasicFrame
    })
}

private fun seasonalFrameTier(frameId: String): RankedTier? {
    if (!frameId.startsWith("season_")) return null
    return RankedTier.entries.firstOrNull { frameId.endsWith("_${it.name.lowercase()}") }
}

private val coralAvatarIds = setOf("target", "crown", DAILY_CHECK_IN_AVATAR_ID, DEFAULT_FEMALE_AVATAR_ID)
