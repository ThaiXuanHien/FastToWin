package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
import com.hienthai.fastowin.protocol.RankedTier

@Composable
fun PlayerAvatar(
    displayName: String,
    avatarId: String?,
    frameId: String = "frame_default",
    size: Dp = 48.dp,
    imageUrl: String = "",
    modifier: Modifier = Modifier
) {
    val colors = avatarFrameColors(frameId)
    val frameName = avatarFrameName(frameId)
    val outerStroke = if (size < 44.dp) 2.dp else 3.dp
    val innerStroke = if (size < 44.dp) 1.dp else 1.5.dp
    val avatarSize = size * if (frameId == "frame_default") 0.82f else 0.76f
    val fallbackFontSize = when {
        size < 40.dp -> 17.sp
        size < 56.dp -> 22.sp
        else -> 30.sp
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerWidth = outerStroke.toPx()
            val innerWidth = innerStroke.toPx()
            val radius = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.sweepGradient(colors),
                radius = radius - outerWidth / 2f,
                style = Stroke(width = outerWidth)
            )
            if (frameId != "frame_default") {
                drawCircle(
                    brush = Brush.sweepGradient(colors.reversed()),
                    radius = radius - outerWidth - innerWidth * 1.5f,
                    style = Stroke(width = innerWidth)
                )
                drawCircle(
                    color = colors.getOrElse(2) { colors.first() },
                    radius = outerWidth * 0.85f,
                    center = center.copy(
                        x = center.x + radius * 0.64f,
                        y = center.y - radius * 0.64f
                    )
                )
            }
        }

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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(playerAvatarEmoji(avatarId), fontSize = fallbackFontSize)
                    }
                }
            )
        }
    }
}

@Composable
private fun avatarFrameColors(frameId: String): List<Color> {
    val seasonTier = seasonalFrameTier(frameId)
    return when (seasonTier) {
        RankedTier.BRONZE -> listOf(BronzeDark, Bronze, BronzeLight, BronzeDark)
        RankedTier.SILVER -> listOf(SilverDark, Silver, SilverLight, Silver, SilverDark)
        RankedTier.GOLD -> listOf(GoldDark, Gold, GoldLight, Gold, GoldDark)
        RankedTier.PLATINUM -> listOf(PlatinumDark, Platinum, PlatinumLight, PlatinumDark)
        RankedTier.DIAMOND -> listOf(DiamondDark, Diamond, DiamondLight, PerfectPurple, DiamondDark)
        RankedTier.MASTER -> listOf(MasterDark, Master, GoldLight, Master, MasterDark)
        RankedTier.CHALLENGER -> listOf(ChallengerRed, Gold, ChallengerCyan, GoldLight, ChallengerRed)
        null -> when (frameId) {
            "frame_bronze" -> listOf(BronzeDark, Bronze, BronzeLight, BronzeDark)
            "frame_silver" -> listOf(SilverDark, Silver, SilverLight, Silver, SilverDark)
            "frame_gold" -> listOf(GoldDark, Gold, GoldLight, Gold, GoldDark)
            "frame_perfect" -> listOf(PerfectCyan, PerfectPurple, PerfectPink, PerfectCyan)
            "frame_persistent" -> listOf(PersistentDark, Persistent, PersistentLight, PersistentGreen, PersistentDark)
            else -> listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outlineVariant)
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

fun playerAvatarEmoji(avatarId: String?): String = when (avatarId) {
    "rocket" -> "🚀"
    "target" -> "🎯"
    "trophy" -> "🏆"
    "crown" -> "👑"
    "star" -> "⭐"
    DAILY_CHECK_IN_AVATAR_ID -> "📅"
    else -> "⚡"
}

private val BronzeDark = Color(0xFF7A431D)
private val Bronze = Color(0xFFCD7F32)
private val BronzeLight = Color(0xFFF2C078)
private val SilverDark = Color(0xFF5E6874)
private val Silver = Color(0xFFB8C2CC)
private val SilverLight = Color(0xFFF4F7FA)
private val GoldDark = Color(0xFF9A6700)
private val Gold = Color(0xFFFFB300)
private val GoldLight = Color(0xFFFFF0A8)
private val PlatinumDark = Color(0xFF52606D)
private val Platinum = Color(0xFF9FB3C8)
private val PlatinumLight = Color(0xFFE4ECF4)
private val DiamondDark = Color(0xFF006D77)
private val Diamond = Color(0xFF36C5F0)
private val DiamondLight = Color(0xFFC8F7FF)
private val MasterDark = Color(0xFF4527A0)
private val Master = Color(0xFF9C6ADE)
private val ChallengerRed = Color(0xFFD32F2F)
private val ChallengerCyan = Color(0xFF00BCD4)
private val PerfectCyan = Color(0xFF00ACC1)
private val PerfectPurple = Color(0xFF7C4DFF)
private val PerfectPink = Color(0xFFE040FB)
private val PersistentDark = Color(0xFF00695C)
private val Persistent = Color(0xFF26A69A)
private val PersistentLight = Color(0xFFA7FFEB)
private val PersistentGreen = Color(0xFF43A047)
