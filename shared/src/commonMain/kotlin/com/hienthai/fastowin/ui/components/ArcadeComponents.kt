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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.fast_to_win_logo_banner
import com.hienthai.fastowin.ui.theme.ArcadePalette
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

enum class ArcadeActionStyle {
    PRIMARY,
    GOLD,
    OUTLINE,
    DANGER
}

/**
 * CTA chuẩn của giao diện 2D Arcade. Lớp đế màu tạo cảm giác nút nổi mà không làm
 * thay đổi kích thước khi nhấn, vì vậy danh sách không bị giật trên Android/iOS.
 */
@Composable
fun ArcadeActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ArcadeActionStyle = ArcadeActionStyle.PRIMARY,
    enabled: Boolean = true,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val shape = RoundedCornerShape(15.dp)
    val colors = when (style) {
        ArcadeActionStyle.PRIMARY -> listOf(ArcadePalette.Blue500, ArcadePalette.Blue700)
        ArcadeActionStyle.GOLD -> listOf(Color(0xFFFFE46D), ArcadePalette.Gold500)
        ArcadeActionStyle.OUTLINE -> listOf(
            ArcadePalette.Navy800.copy(alpha = 0.36f),
            ArcadePalette.Navy900.copy(alpha = 0.36f)
        )
        ArcadeActionStyle.DANGER -> listOf(ArcadePalette.Coral400, ArcadePalette.Coral800)
    }
    val disabledColors = listOf(Color(0xFF33466F), Color(0xFF27365A))
    val contentColor = when {
        !enabled -> Color(0xFF8EA0C7)
        style == ArcadeActionStyle.GOLD -> ArcadePalette.Ink
        else -> ArcadePalette.White
    }
    val shadowColor = when (style) {
        ArcadeActionStyle.PRIMARY -> Color(0xFF123994)
        ArcadeActionStyle.GOLD -> Color(0xFFB56B14)
        ArcadeActionStyle.OUTLINE -> Color.Transparent
        ArcadeActionStyle.DANGER -> Color(0xFF761B30)
    }
    val border = if (style == ArcadeActionStyle.OUTLINE) {
        BorderStroke(1.dp, ArcadePalette.OutlineDark)
    } else {
        null
    }

    Box(
        modifier = modifier
            .height(55.dp)
            .semantics { if (!enabled) disabled() }
    ) {
        if (enabled && shadowColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .offset(y = 5.dp)
                    .background(shadowColor, shape)
            )
        }
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = shape,
            color = Color.Transparent,
            contentColor = contentColor,
            border = border
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(if (enabled) colors else disabledColors), shape)
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (content != null) {
                    content()
                } else {
                    icon?.let {
                        Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                        Box(modifier = Modifier.width(7.dp))
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = contentColor
                    )
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
    accent: Color = ArcadePalette.Violet600,
    onIconClick: (() -> Unit)? = null,
    iconContentDescription: String? = null
) {
    val shape = RoundedCornerShape(24.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 142.dp)
            .background(
                Brush.linearGradient(
                    listOf(ArcadePalette.Blue700, accent, ArcadePalette.Coral600.copy(alpha = 0.84f))
                ),
                shape
            )
            .border(1.dp, ArcadePalette.Blue300.copy(alpha = 0.7f), shape)
            .padding(18.dp)
    ) {
        val stack = maxWidth < 330.dp || LocalDensity.current.fontScale >= 1.4f
        Column(
            modifier = Modifier.fillMaxWidth(if (stack) 1f else 0.72f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                kicker,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFE28B)
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
                color = Color(0xFFE5EDFF)
            )
        }
        if (!stack) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(76.dp)
                    .rotate(8f)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFFFE36F), Color(0xFFFF8D37))),
                        RoundedCornerShape(23.dp)
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(23.dp))
                    .then(
                        if (onIconClick == null) Modifier else Modifier.clickable(onClick = onIconClick)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = iconContentDescription,
                    tint = ArcadePalette.Navy800,
                    modifier = Modifier.size(40.dp)
                )
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
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                contentColor = Color.White,
                border = BorderStroke(1.dp, Color(0xFF5680CA)),
                shadowElevation = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF123269), Color(0xFF091D43)))
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA9BADC)
                        )
                    }
                    content()
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
            .background(ArcadePalette.Navy900, RoundedCornerShape(16.dp))
            .border(1.dp, ArcadePalette.OutlineDark, RoundedCornerShape(16.dp))
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
                color = if (selected) ArcadePalette.Blue600 else Color.Transparent,
                contentColor = when {
                    !itemEnabled -> Color(0xFF657AA8)
                    selected -> Color.White
                    else -> ArcadePalette.Blue100
                },
                shadowElevation = if (selected) 3.dp else 0.dp
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
            .background(ArcadePalette.Navy900, RoundedCornerShape(16.dp))
            .border(1.dp, ArcadePalette.OutlineDark, RoundedCornerShape(16.dp))
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
                color = if (selected) ArcadePalette.Blue600 else Color.Transparent,
                contentColor = when {
                    !itemEnabled -> Color(0xFF657AA8)
                    selected -> Color.White
                    else -> ArcadePalette.Blue100
                },
                shadowElevation = if (selected) 3.dp else 0.dp
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
        label = "XEM THÊM ($remaining)",
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
        color = if (selected) Color(0xFF1E5DEA) else Color(0xFF102B60),
        contentColor = if (enabled) Color.White else Color(0xFF657AA8),
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFF79ABFF) else Color(0xFF34578F)
        ),
        shadowElevation = if (selected) 3.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
        }
    }
}

/** Static arcade backdrop: decorative only, so it remains comfortable with reduced motion enabled. */
@Composable
fun ArcadeBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val darkBackdrop = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backdropColors = if (darkBackdrop) {
        listOf(Color(0xFF071837), Color(0xFF06132F), Color(0xFF071A3B))
    } else {
        listOf(Color(0xFFF5F8FF), Color(0xFFE9F1FF), Color(0xFFF8FAFF))
    }
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(backdropColors)
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF7B4DE8).copy(alpha = if (darkBackdrop) 0.20f else 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(size.width, 0f),
                    radius = size.maxDimension * 0.72f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2F83FF).copy(alpha = if (darkBackdrop) 0.15f else 0.09f),
                        Color.Transparent
                    ),
                    center = Offset(0f, size.height * 0.38f),
                    radius = size.maxDimension * 0.58f
                )
            )
            val step = 22.dp.toPx()
            val grid = if (darkBackdrop) {
                Color.White.copy(alpha = 0.035f)
            } else {
                Color(0xFF31588E).copy(alpha = 0.055f)
            }
            var x = 0f
            while (x <= size.width) {
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }
        content()
    }
}

@Composable
fun ArcadePanel(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
        shadowElevation = 2.dp,
        content = content
    )
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
            .border(2.dp, ArcadePalette.Blue300.copy(alpha = 0.7f), shape)
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
        1 -> ArcadePalette.Gold400
        2 -> Color(0xFFC8D4EA)
        3 -> Color(0xFFE59655)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (rank in 1..3) ArcadePalette.Navy950 else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier.size(24.dp),
        shape = CircleShape,
        color = color,
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.48f))
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
