package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.network.toAvatarImageUrl
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
import com.hienthai.fastowin.protocol.DEFAULT_FEMALE_AVATAR_ID
import com.hienthai.fastowin.protocol.FRAME_CATALOG
import com.hienthai.fastowin.protocol.RankedTier
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.localization.localizedRankedTierName
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService
import com.hienthai.fastowin.localization.rankedTierTextKey
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_frame_bronze
import com.hienthai.fastowin.resources.arcade_frame_challenger
import com.hienthai.fastowin.resources.arcade_frame_champion
import com.hienthai.fastowin.resources.arcade_frame_default
import com.hienthai.fastowin.resources.arcade_frame_diamond
import com.hienthai.fastowin.resources.arcade_frame_dragon_might
import com.hienthai.fastowin.resources.arcade_frame_emperor
import com.hienthai.fastowin.resources.arcade_frame_glory
import com.hienthai.fastowin.resources.arcade_frame_gold
import com.hienthai.fastowin.resources.arcade_frame_immortal
import com.hienthai.fastowin.resources.arcade_frame_legend
import com.hienthai.fastowin.resources.arcade_frame_lightning
import com.hienthai.fastowin.resources.arcade_frame_master
import com.hienthai.fastowin.resources.arcade_frame_peerless
import com.hienthai.fastowin.resources.arcade_frame_perfect
import com.hienthai.fastowin.resources.arcade_frame_persistent
import com.hienthai.fastowin.resources.arcade_frame_platinum
import com.hienthai.fastowin.resources.arcade_frame_silver
import com.hienthai.fastowin.resources.arcade_frame_speed_shadow
import com.hienthai.fastowin.resources.arcade_frame_supreme
import com.hienthai.fastowin.resources.arcade_frame_unyielding
import com.hienthai.fastowin.resources.arcade_frame_veteran
import com.hienthai.fastowin.resources.arcade_frame_warrior
import com.hienthai.fastowin.resources.arcade_frame_wildfire
import com.hienthai.fastowin.resources.avatar_player_blue
import com.hienthai.fastowin.resources.avatar_player_coral
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun PlayerAvatar(
    displayName: String,
    avatarId: String?,
    userId: String? = null,
    frameId: String = "frame_default",
    size: Dp = 48.dp,
    imageUrl: String = "",
    modifier: Modifier = Modifier
) {
    val avatarServerUrl = LocalAvatarServerUrl.current
    val avatarRevision = LocalAvatarRevision.current
    val resolvedImageUrl = imageUrl.ifBlank {
        userId
            ?.takeIf { it.isNotBlank() && avatarServerUrl.isNotBlank() }
            ?.let { avatarServerUrl.toAvatarImageUrl(it, avatarRevision) }
            .orEmpty()
    }
    val frameName = localizedAvatarFrameName(frameId)
    val avatarDescription = localized(TextKey.AvatarOfPlayer, "player" to displayName)
    val frameResource = avatarFrameResource(frameId)
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
            NetworkImage(
                url = resolvedImageUrl,
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

        Image(
            painter = painterResource(frameResource),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private val LocalAvatarServerUrl = staticCompositionLocalOf { "" }
private val LocalAvatarRevision = staticCompositionLocalOf { 0L }

@Composable
fun AvatarImageProvider(
    serverUrl: String,
    revision: Long,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalAvatarServerUrl provides serverUrl,
        LocalAvatarRevision provides revision,
        content = content
    )
}

private fun avatarFrameResource(frameId: String): DrawableResource {
    return when (avatarFrameAssetName(frameId)) {
        "arcade_frame_bronze" -> Res.drawable.arcade_frame_bronze
        "arcade_frame_silver" -> Res.drawable.arcade_frame_silver
        "arcade_frame_gold" -> Res.drawable.arcade_frame_gold
        "arcade_frame_platinum" -> Res.drawable.arcade_frame_platinum
        "arcade_frame_diamond" -> Res.drawable.arcade_frame_diamond
        "arcade_frame_master" -> Res.drawable.arcade_frame_master
        "arcade_frame_challenger" -> Res.drawable.arcade_frame_challenger
        "arcade_frame_perfect" -> Res.drawable.arcade_frame_perfect
        "arcade_frame_persistent" -> Res.drawable.arcade_frame_persistent
        "arcade_frame_lightning" -> Res.drawable.arcade_frame_lightning
        "arcade_frame_wildfire" -> Res.drawable.arcade_frame_wildfire
        "arcade_frame_warrior" -> Res.drawable.arcade_frame_warrior
        "arcade_frame_veteran" -> Res.drawable.arcade_frame_veteran
        "arcade_frame_glory" -> Res.drawable.arcade_frame_glory
        "arcade_frame_unyielding" -> Res.drawable.arcade_frame_unyielding
        "arcade_frame_legend" -> Res.drawable.arcade_frame_legend
        "arcade_frame_emperor" -> Res.drawable.arcade_frame_emperor
        "arcade_frame_speed_shadow" -> Res.drawable.arcade_frame_speed_shadow
        "arcade_frame_champion" -> Res.drawable.arcade_frame_champion
        "arcade_frame_immortal" -> Res.drawable.arcade_frame_immortal
        "arcade_frame_dragon_might" -> Res.drawable.arcade_frame_dragon_might
        "arcade_frame_supreme" -> Res.drawable.arcade_frame_supreme
        "arcade_frame_peerless" -> Res.drawable.arcade_frame_peerless
        else -> Res.drawable.arcade_frame_default
    }
}

internal fun avatarFrameAssetName(frameId: String): String {
    seasonalFrameTier(frameId)?.let { tier ->
        return when (tier) {
            RankedTier.BRONZE -> "arcade_frame_bronze"
            RankedTier.SILVER -> "arcade_frame_silver"
            RankedTier.GOLD -> "arcade_frame_gold"
            RankedTier.PLATINUM -> "arcade_frame_platinum"
            RankedTier.DIAMOND -> "arcade_frame_diamond"
            RankedTier.MASTER -> "arcade_frame_master"
            RankedTier.CHALLENGER -> "arcade_frame_challenger"
        }
    }
    return when (frameId) {
        "frame_bronze" -> "arcade_frame_bronze"
        "frame_silver" -> "arcade_frame_silver"
        "frame_gold" -> "arcade_frame_gold"
        "frame_perfect" -> "arcade_frame_perfect"
        "frame_persistent" -> "arcade_frame_persistent"
        "frame_lightning" -> "arcade_frame_lightning"
        "frame_wildfire" -> "arcade_frame_wildfire"
        "frame_warrior" -> "arcade_frame_warrior"
        "frame_veteran" -> "arcade_frame_veteran"
        "frame_diamond" -> "arcade_frame_diamond"
        "frame_challenger" -> "arcade_frame_challenger"
        "frame_glory" -> "arcade_frame_glory"
        "frame_unyielding" -> "arcade_frame_unyielding"
        "frame_legend" -> "arcade_frame_legend"
        "frame_emperor" -> "arcade_frame_emperor"
        "frame_speed_shadow" -> "arcade_frame_speed_shadow"
        "frame_champion" -> "arcade_frame_champion"
        "frame_immortal" -> "arcade_frame_immortal"
        "frame_dragon_might" -> "arcade_frame_dragon_might"
        "frame_supreme" -> "arcade_frame_supreme"
        "frame_peerless" -> "arcade_frame_peerless"
        else -> "arcade_frame_default"
    }
}

fun avatarFrameName(frameId: String): String {
    val localization = LocalizationService(AppLanguage.VIETNAMESE)
    seasonalFrameTier(frameId)?.let {
        return localization.text(
            TextKey.SeasonalFrame,
            mapOf("tier" to localization.text(rankedTierTextKey(it)))
        )
    }
    FRAME_CATALOG.firstOrNull { it.id == frameId }?.let { return it.fallbackName }
    return localization.text(when (frameId) {
        "frame_bronze" -> TextKey.BronzeFrame
        "frame_silver" -> TextKey.SilverFrame
        "frame_gold" -> TextKey.GoldFrame
        "frame_perfect" -> TextKey.PerfectFrame
        "frame_persistent" -> TextKey.PersistentFrameName
        else -> TextKey.BasicFrame
    })
}

@Composable
private fun localizedAvatarFrameName(frameId: String): String {
    seasonalFrameTier(frameId)?.let {
        return localized(TextKey.SeasonalFrame, "tier" to localizedRankedTierName(it))
    }
    FRAME_CATALOG.firstOrNull { it.id == frameId }?.let { return it.fallbackName }
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
