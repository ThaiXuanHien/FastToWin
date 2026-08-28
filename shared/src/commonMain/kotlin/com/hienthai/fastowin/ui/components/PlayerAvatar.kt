package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
import com.hienthai.fastowin.protocol.DEFAULT_FEMALE_AVATAR_ID
import com.hienthai.fastowin.protocol.RankedTier
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_frame_bronze
import com.hienthai.fastowin.resources.arcade_frame_challenger
import com.hienthai.fastowin.resources.arcade_frame_default
import com.hienthai.fastowin.resources.arcade_frame_diamond
import com.hienthai.fastowin.resources.arcade_frame_gold
import com.hienthai.fastowin.resources.arcade_frame_master
import com.hienthai.fastowin.resources.arcade_frame_perfect
import com.hienthai.fastowin.resources.arcade_frame_persistent
import com.hienthai.fastowin.resources.arcade_frame_platinum
import com.hienthai.fastowin.resources.arcade_frame_silver
import com.hienthai.fastowin.resources.avatar_player_blue
import com.hienthai.fastowin.resources.avatar_player_coral
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun PlayerAvatar(
    displayName: String,
    avatarId: String?,
    frameId: String = "frame_default",
    size: Dp = 48.dp,
    imageUrl: String = "",
    modifier: Modifier = Modifier
) {
    val frameName = avatarFrameName(frameId)
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
                contentDescription = "Ảnh đại diện của $displayName"
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
                url = imageUrl,
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

private fun avatarFrameResource(frameId: String): DrawableResource {
    return when (seasonalFrameTier(frameId)) {
        RankedTier.BRONZE -> Res.drawable.arcade_frame_bronze
        RankedTier.SILVER -> Res.drawable.arcade_frame_silver
        RankedTier.GOLD -> Res.drawable.arcade_frame_gold
        RankedTier.PLATINUM -> Res.drawable.arcade_frame_platinum
        RankedTier.DIAMOND -> Res.drawable.arcade_frame_diamond
        RankedTier.MASTER -> Res.drawable.arcade_frame_master
        RankedTier.CHALLENGER -> Res.drawable.arcade_frame_challenger
        null -> when (frameId) {
            "frame_bronze" -> Res.drawable.arcade_frame_bronze
            "frame_silver" -> Res.drawable.arcade_frame_silver
            "frame_gold" -> Res.drawable.arcade_frame_gold
            "frame_perfect" -> Res.drawable.arcade_frame_perfect
            "frame_persistent" -> Res.drawable.arcade_frame_persistent
            else -> Res.drawable.arcade_frame_default
        }
    }
}

fun avatarFrameName(frameId: String): String {
    seasonalFrameTier(frameId)?.let { return "Khung mùa • ${it.displayName}" }
    return when (frameId) {
        "frame_bronze" -> "Khung Đồng"
        "frame_silver" -> "Khung Bạc"
        "frame_gold" -> "Khung Vàng"
        "frame_perfect" -> "Khung Hoàn hảo"
        "frame_persistent" -> "Khung Bền bỉ"
        else -> "Khung cơ bản"
    }
}

private fun seasonalFrameTier(frameId: String): RankedTier? {
    if (!frameId.startsWith("season_")) return null
    return RankedTier.entries.firstOrNull { frameId.endsWith("_${it.name.lowercase()}") }
}

private val coralAvatarIds = setOf("target", "crown", DAILY_CHECK_IN_AVATAR_ID, DEFAULT_FEMALE_AVATAR_ID)
