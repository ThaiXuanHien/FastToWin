package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.platformRefreshInput
import com.hienthai.fastowin.ui.theme.A1Palette
import com.hienthai.fastowin.ui.format.compactNumber
import com.hienthai.fastowin.ui.format.fullNumber
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics

private data class HeaderWalletNavigation(
    val onGold: () -> Unit = {},
    val onGems: () -> Unit = {}
)

private val LocalHeaderWalletNavigation = staticCompositionLocalOf { HeaderWalletNavigation() }

@Composable
fun HeaderWalletNavigationProvider(
    onGold: () -> Unit,
    onGems: () -> Unit,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalHeaderWalletNavigation provides HeaderWalletNavigation(onGold, onGems),
        content = content
    )
}

@Composable
fun FastToWinHeader(
    title: String,
    gold: Int,
    gems: Int,
    unreadNotifications: Int,
    onNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    applySafeDrawingInset: Boolean = true,
    showNotifications: Boolean = true,
    showBalances: Boolean = true,
    showBrand: Boolean = false,
    subtitle: String? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val walletNavigation = LocalHeaderWalletNavigation.current
    // A1: thanh trên cùng nằm trực tiếp trên nền trời (không có dải màu riêng).
    BoxWithConstraints(modifier = modifier.fillMaxWidth().testTag("app_header")) {
    val hasTitle = title.isNotBlank() && !(showBrand && onBack == null)
    val fontScale = LocalDensity.current.fontScale
    // Màn hẹp hoặc chữ lớn: đưa ô tiền tệ xuống hàng thứ hai để tiêu đề còn chỗ đọc.
    val stackBalances = hasTitle && showBalances && (maxWidth < 360.dp || (maxWidth < 480.dp && fontScale >= 1.3f))
    val showPlus = !hasTitle || maxWidth >= 600.dp
    val balances: @Composable () -> Unit = {
        HeaderCurrency(amount = gold, label = localized(TextKey.Gold), isGem = false, showPlus = showPlus, onClick = walletNavigation.onGold)
        HeaderCurrency(amount = gems, label = localized(TextKey.Gems), isGem = true, showPlus = showPlus, onClick = walletNavigation.onGems)
    }
    Column(Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (applySafeDrawingInset) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    )
                } else {
                    Modifier
                }
            )
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onBack != null) {
            ArcadeHeaderIconButton(onClick = onBack) {
                Icon(backIcon, contentDescription = localized(TextKey.Back))
            }
        }
        if (showBrand && onBack == null) {
            ArcadeHeaderLogo()
        }
        if (hasTitle) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
            ) {
                A1OutlinedText(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = A1Palette.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (showBalances && !stackBalances) balances()
        if (!hasTitle) Spacer(modifier = Modifier.weight(1f))
        if (showNotifications) {
            // Số thông báo nằm ở góc ngoài của nút cam (như thiết kế), không đè lên icon.
            Box {
                ArcadeHeaderIconButton(onClick = onNotifications, accent = true) {
                    Icon(Icons.Default.Notifications, contentDescription = localized(TextKey.Notifications))
                }
                if (unreadNotifications > 0) {
                    A1Badge(
                        if (unreadNotifications > 99) "99+" else unreadNotifications.toString(),
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp),
                        decorative = false
                    )
                }
            }
        }
        actions()
    }
    if (stackBalances) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) { balances() }
    }
    }
    }
}

/**
 * Nút icon của A1 (vùng chạm 48dp): mặc định trắng viền navy; [accent] = cam (như nút thông báo
 * trong thiết kế). Đế đặc 3dp phía dưới.
 */
@Composable
fun ArcadeHeaderIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    /** Nút xanh (".ib.b" của thiết kế), vd. chia sẻ mã phòng. */
    blue: Boolean = false,
    content: @Composable () -> Unit
) {
    val face = when {
        accent -> Color(0xFFFF8A3D)
        blue -> A1Palette.Blue2
        else -> Color.White
    }
    val edge = when {
        accent -> Color(0xFF9A3A00)
        blue -> A1Palette.BlueDeep
        else -> A1Palette.Line
    }
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 48.dp)
            .a1Card(radius = 15.dp, depth = 3.dp, background = face, border = edge)
            .clip(RoundedCornerShape(15.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val tint = when {
            !enabled -> A1Palette.Muted
            accent || blue -> Color.White
            else -> A1Palette.Line
        }
        CompositionLocalProvider(LocalContentColor provides tint) {
            content()
        }
    }
}

@Composable
fun HeaderRefreshAction(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    visible: Boolean = platformRefreshInput().pointerRefreshEnabled
) {
    if (!visible) return
    ArcadeHeaderIconButton(
        onClick = onRefresh,
        enabled = !isRefreshing,
        modifier = Modifier.testTag(if (isRefreshing) "header_refresh_busy" else "header_refresh")
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = A1Palette.Line
            )
        } else {
            Icon(Icons.Default.Refresh, contentDescription = localized(TextKey.Retry))
        }
    }
}

/**
 * Ô tiền tệ A1 (".pill"): nền trắng 92%, viền navy 2dp, bóng trong ở mép dưới, đồng xu/viên ngọc
 * 24dp, số Baloo 15sp và nút "+" xanh lá mở cửa hàng.
 */
@Composable
private fun HeaderCurrency(amount: Int, label: String, isGem: Boolean, showPlus: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val shown = compactNumber(amount.toLong())
    val spoken = "${fullNumber(amount.toLong())} $label"
    Row(
        modifier = Modifier
            .testTag(if (isGem) "header_gem" else "header_gold")
            .heightIn(min = 48.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(Color.White.copy(alpha = 0.92f))
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = 0.08f), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 3.dp.toPx()))
                }
                .border(2.dp, A1Palette.Line, shape)
                .padding(start = 9.dp, end = if (showPlus) 6.dp else 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isGem) A1GemIcon(size = 24.dp) else A1CoinIcon(size = 24.dp)
            Text(
                shown,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 18.sp),
                fontWeight = FontWeight.ExtraBold,
                color = A1Palette.Ink,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { }
            )
            if (showPlus) A1PlusChip()
        }
    }
}

/** Nút "+" nhỏ của ô tiền tệ: vuông bo 7dp, xanh lá, dấu cộng trắng vẽ bằng hai thanh. */
@Composable
private fun A1PlusChip() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(Color(0xFF5BD94A), RoundedCornerShape(7.dp))
            .border(2.dp, A1Palette.GreenDeep, RoundedCornerShape(7.dp))
            .drawBehind {
                val c = center
                val long = 10.dp.toPx()
                val thick = 3.dp.toPx()
                val r = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                drawRoundRect(Color.White, androidx.compose.ui.geometry.Offset(c.x - long / 2, c.y - thick / 2), androidx.compose.ui.geometry.Size(long, thick), r)
                drawRoundRect(Color.White, androidx.compose.ui.geometry.Offset(c.x - thick / 2, c.y - long / 2), androidx.compose.ui.geometry.Size(thick, long), r)
            }
    )
}
