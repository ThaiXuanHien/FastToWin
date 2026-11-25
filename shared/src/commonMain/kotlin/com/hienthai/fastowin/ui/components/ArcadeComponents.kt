package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.fast_to_win_logo_banner
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.ui.theme.A1Palette
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

enum class ArcadeActionStyle {
    PRIMARY,
    GOLD,
    OUTLINE,
    DANGER,
    SUCCESS,
    SPECIAL
}

/**
 * Nút 3D chuẩn A1: mặt nút dải màu, viền đậm 3dp và đế đặc phía dưới.
 * Khi nhấn, mặt nút hạ xuống đúng bằng độ cao đế nên bố cục không bị giật.
 * Giữ tên/tham số cũ để mọi màn hình dùng lại được: PRIMARY = xanh dương, GOLD = vàng (CTA chính),
 * OUTLINE = trắng viền navy, DANGER = đỏ; thêm SUCCESS (xanh lá) và SPECIAL (tím).
 */
@Composable
fun ArcadeActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ArcadeActionStyle = ArcadeActionStyle.PRIMARY,
    enabled: Boolean = true,
    compact: Boolean = false,
    /** Nút CTA lớn (vd. CHƠI NGAY): chữ và icon to hơn. */
    hero: Boolean = false,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val colors = if (!enabled) A1ButtonDefaults.Disabled else when (style) {
        ArcadeActionStyle.PRIMARY -> A1ButtonDefaults.Blue
        ArcadeActionStyle.GOLD -> A1ButtonDefaults.Yellow
        ArcadeActionStyle.OUTLINE -> A1ButtonDefaults.White
        ArcadeActionStyle.DANGER -> A1ButtonDefaults.Red
        ArcadeActionStyle.SUCCESS -> A1ButtonDefaults.Green
        ArcadeActionStyle.SPECIAL -> A1ButtonDefaults.Purple
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = if (compact) A1Dimens.SoftDepth else A1Dimens.CardDepth
    val radius = if (compact) 14.dp else A1Dimens.ButtonRadius

    A1RaisedSurface(
        colors = colors,
        radius = radius,
        depth = depth,
        pressed = pressed && enabled,
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) disabled()
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 44.dp else 50.dp)
                    .padding(horizontal = if (compact) 12.dp else 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (content != null) {
                    content()
                } else {
                    icon?.let {
                        Icon(it, contentDescription = null, modifier = Modifier.size(if (hero) 34.dp else if (compact) 20.dp else 24.dp))
                        Box(modifier = Modifier.width(8.dp))
                    }
                    if (hero && enabled) {
                        // CTA lớn: chữ trắng viền đậm theo màu đế (như "CHƠI NGAY" trong thiết kế).
                        A1OutlinedText(
                            text = label,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                shadow = a1TextShadow(colors.textShadow)
                            ),
                            outline = colors.textShadow ?: colors.border,
                            outlineWidth = 4.dp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            label,
                            style = (if (hero) MaterialTheme.typography.headlineMedium else if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                                .copy(shadow = a1TextShadow(colors.textShadow)),
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.content,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArcadeIconHero(
    kicker: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = A1Palette.Purple2,
    onIconClick: (() -> Unit)? = null,
    iconContentDescription: String? = null,
    /** An uploaded image shown in the badge instead of [icon]; the icon stays as its fallback. */
    iconImageUrl: String = ""
) {
    val shape = RoundedCornerShape(24.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 142.dp)
            .a1Card(radius = 24.dp, background = Color.Transparent)
            .background(Brush.linearGradient(listOf(A1Palette.Blue2, accent)), shape)
            .border(A1Dimens.Border, A1Palette.Line, shape)
            .padding(18.dp)
    ) {
        val stack = maxWidth < 330.dp || LocalDensity.current.fontScale >= 1.4f
        Column(
            modifier = Modifier
                .fillMaxWidth(if (stack) 1f else 0.72f)
                .padding(end = if (stack) 64.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                kicker,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = A1Palette.Yellow1
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
        if (stack) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(52.dp)
                    .rotate(8f)
                    .background(
                        Brush.verticalGradient(listOf(A1Palette.Yellow1, A1Palette.Yellow3)),
                        RoundedCornerShape(18.dp)
                    )
                    .border(A1Dimens.Border, A1Palette.Line, RoundedCornerShape(18.dp))
                    .then(
                        if (onIconClick == null) Modifier else Modifier.clickable(onClick = onIconClick)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (iconImageUrl.isBlank()) Icon(
                    icon,
                    contentDescription = iconContentDescription,
                    tint = A1Palette.YellowBorder,
                    modifier = Modifier.size(30.dp)
                ) else NetworkImage(
                    iconImageUrl,
                    Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(18.dp)),
                    contentDescription = iconContentDescription,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                ) {
                    Icon(icon, contentDescription = iconContentDescription, tint = A1Palette.YellowBorder,
                        modifier = Modifier.size(30.dp))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(76.dp)
                    .rotate(8f)
                    .background(
                        Brush.verticalGradient(listOf(A1Palette.Yellow1, A1Palette.Yellow3)),
                        RoundedCornerShape(23.dp)
                    )
                    .border(A1Dimens.Border, A1Palette.Line, RoundedCornerShape(23.dp))
                    .then(
                        if (onIconClick == null) Modifier else Modifier.clickable(onClick = onIconClick)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (iconImageUrl.isBlank()) Icon(
                    icon,
                    contentDescription = iconContentDescription,
                    tint = A1Palette.YellowBorder,
                    modifier = Modifier.size(40.dp)
                ) else NetworkImage(
                    iconImageUrl,
                    Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(23.dp)),
                    contentDescription = iconContentDescription,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                ) {
                    Icon(icon, contentDescription = iconContentDescription, tint = A1Palette.YellowBorder,
                        modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
fun ArcadeDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showCloseButton: Boolean = false,
    scrollableContent: Boolean = false,
    fixedActions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = arcadeDialogProperties()
    ) {
        NativeDialogInputScope {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val dialogMaxHeight = (maxHeight - 24.dp).coerceAtLeast(240.dp)
                Surface(
                    modifier = modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .heightIn(max = dialogMaxHeight)
                        .imePadding(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    contentColor = A1Palette.Ink,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("arcade_dialog")
                            .a1Card(radius = 24.dp)
                            .then(if (scrollableContent && fixedActions == null) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (showCloseButton) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                IconButton(
                                    onClick = onDismissRequest,
                                    modifier = Modifier.size(48.dp).testTag("arcade_dialog_close")
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = localized(TextKey.Close), tint = A1Palette.Line)
                                }
                            }
                        } else {
                            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        }
                        subtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = A1Palette.Muted
                            )
                        }
                        if (fixedActions != null) {
                            Column(
                                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                                    .testTag("arcade_dialog_body"),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) { content() }
                            fixedActions()
                        } else content()
                    }
                }
            }
        }
    }
}

/** Segmented tabs used throughout the arcade design (rooms, wallet, history and settings). */
@Composable
fun ArcadeSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (Int) -> Boolean = { true },
    itemTestTag: (Int) -> String? = { null }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(A1Palette.SurfaceAlt, RoundedCornerShape(16.dp))
            .border(A1Dimens.Border, A1Palette.Line, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            val itemEnabled = enabled(index)
            Surface(
                onClick = { onSelected(index) },
                enabled = itemEnabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics {
                        this.selected = selected
                        role = Role.Tab
                    }
                    .then(itemTestTag(index)?.let { Modifier.testTag(it) } ?: Modifier),
                shape = RoundedCornerShape(12.dp),
                color = if (selected) A1Palette.Yellow2 else Color.Transparent,
                contentColor = when {
                    !itemEnabled -> A1Palette.Muted.copy(alpha = 0.6f)
                    selected -> A1Palette.Ink
                    else -> A1Palette.Muted
                },
                border = if (selected) BorderStroke(2.dp, A1Palette.Line) else null,
                shadowElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Segmented tabs dành cho nhóm có nhiều nhãn dài. Các tab giữ kích thước chạm
 * và chiều rộng đọc được trên điện thoại nhỏ, còn cả dải có thể cuộn ngang.
 */
@Composable
fun ArcadeScrollableSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minItemWidth: androidx.compose.ui.unit.Dp = 92.dp,
    enabled: (Int) -> Boolean = { true },
    itemTestTag: (Int) -> String? = { null }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(A1Palette.SurfaceAlt, RoundedCornerShape(16.dp))
            .border(A1Dimens.Border, A1Palette.Line, RoundedCornerShape(16.dp))
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            val itemEnabled = enabled(index)
            Surface(
                onClick = { onSelected(index) },
                enabled = itemEnabled,
                modifier = Modifier
                    .widthIn(min = minItemWidth)
                    .heightIn(min = 48.dp)
                    .semantics {
                        this.selected = selected
                        role = Role.Tab
                    }
                    .then(itemTestTag(index)?.let { Modifier.testTag(it) } ?: Modifier),
                shape = RoundedCornerShape(12.dp),
                color = if (selected) A1Palette.Yellow2 else Color.Transparent,
                contentColor = when {
                    !itemEnabled -> A1Palette.Muted.copy(alpha = 0.6f)
                    selected -> A1Palette.Ink
                    else -> A1Palette.Muted
                },
                border = if (selected) BorderStroke(2.dp, A1Palette.Line) else null,
                shadowElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

const val DEFAULT_ARCADE_PAGE_SIZE = 20

internal fun nextArcadePageItemCount(
    currentVisibleCount: Int,
    totalItemCount: Int,
    pageSize: Int = DEFAULT_ARCADE_PAGE_SIZE
): Int = (currentVisibleCount.coerceAtLeast(0) + pageSize.coerceAtLeast(1))
    .coerceAtMost(totalItemCount.coerceAtLeast(0))

@Composable
fun ArcadeLoadMoreButton(
    visibleItemCount: Int,
    totalItemCount: Int,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val remaining = (totalItemCount - visibleItemCount).coerceAtLeast(0)
    if (remaining == 0) return

    ArcadeActionButton(
        label = localized(TextKey.ViewMoreCount, "count" to remaining),
        onClick = onLoadMore,
        style = ArcadeActionStyle.OUTLINE,
        modifier = modifier
            .fillMaxWidth()
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
    )
}

@Composable
fun ArcadeChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) A1Palette.Highlight else Color.White,
        contentColor = if (enabled) A1Palette.Ink else A1Palette.Muted.copy(alpha = 0.6f),
        border = BorderStroke(2.dp, if (selected) A1Palette.Line else A1Palette.SurfaceAlt2),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
        }
    }
}

/** Nền chung của mọi màn hình: trời xanh A1 (tĩnh, an toàn với giảm chuyển động). */
@Composable
fun ArcadeBackdrop(
    modifier: Modifier = Modifier,
    showHills: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    A1SkyBackdrop(modifier = modifier, showHills = showHills, content = content)
}

@Composable
fun ArcadePanel(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    // [accent] giữ lại cho tương thích; thẻ A1 luôn viền navy để thống nhất.
    Box(modifier = modifier.a1Card()) {
        CompositionLocalProvider(LocalContentColor provides A1Palette.Ink) { content() }
    }
}

@Composable
fun ArcadeBrandLockup(modifier: Modifier = Modifier, compact: Boolean = false) {
    val shape = RoundedCornerShape(if (compact) 14.dp else 22.dp)
    Image(
        painter = painterResource(Res.drawable.fast_to_win_logo_banner),
        contentDescription = "Fast To Win",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .widthIn(max = if (compact) 240.dp else 420.dp)
            .fillMaxWidth()
            .aspectRatio(3f)
            .clip(shape)
            .border(A1Dimens.Border, A1Palette.Line, shape)
    )
}

@Composable
fun ArcadeHeaderLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.fast_to_win_logo_banner),
        contentDescription = "Fast To Win",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .width(116.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
    )
}

@Composable
fun ArcadeFeatureHero(
    illustration: DrawableResource,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onIllustrationClick: (() -> Unit)? = null,
    illustrationContentDescription: String? = null
) {
    ArcadePanel(modifier = modifier.fillMaxWidth(), accent = accent) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val stackVertically = maxWidth < 340.dp || (maxWidth < 520.dp && fontScale >= 1.3f)

            if (stackVertically) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ArcadeFeatureHeroText(
                        title = title,
                        subtitle = subtitle,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ArcadeFeatureHeroIllustration(
                        illustration = illustration,
                        onClick = onIllustrationClick,
                        contentDescription = illustrationContentDescription,
                        modifier = Modifier.size(118.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp)
                        .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArcadeFeatureHeroText(
                        title = title,
                        subtitle = subtitle,
                        modifier = Modifier.weight(1f)
                    )
                    ArcadeFeatureHeroIllustration(
                        illustration = illustration,
                        onClick = onIllustrationClick,
                        contentDescription = illustrationContentDescription,
                        modifier = Modifier.size(142.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArcadeFeatureHeroText(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArcadeFeatureHeroIllustration(
    illustration: DrawableResource,
    onClick: (() -> Unit)?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val interactionModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Image(
        painter = painterResource(illustration),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.then(interactionModifier)
    )
}

@Composable
fun ArcadeEmptyState(
    illustration: DrawableResource,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(
            painter = painterResource(illustration),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(148.dp)
        )
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ArcadeRankBadge(rank: Int, modifier: Modifier = Modifier) {
    val color = when (rank) {
        1 -> A1Palette.Gold
        2 -> Color(0xFFD5DEEA)
        3 -> Color(0xFFF0A868)
        else -> A1Palette.SurfaceAlt
    }
    val contentColor = A1Palette.Ink
    Surface(
        modifier = modifier.size(24.dp),
        shape = CircleShape,
        color = color,
        border = BorderStroke(2.dp, A1Palette.Line)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                rank.toString(),
                color = contentColor,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
