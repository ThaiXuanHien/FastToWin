package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.hienthai.fastowin.ui.theme.ArcadePalette

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
    Surface(
        modifier = modifier.fillMaxWidth().testTag("app_header"),
        color = if (applySafeDrawingInset) ArcadePalette.Navy900 else Color.Transparent,
        contentColor = ArcadePalette.White
    ) {
        Column {
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
                    .heightIn(min = 58.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (onBack != null) {
                    ArcadeHeaderIconButton(onClick = onBack) {
                        Icon(backIcon, contentDescription = "Quay lại")
                    }
                }
                if (showBrand && onBack == null) {
                    ArcadeHeaderLogo()
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = ArcadePalette.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = ArcadePalette.White.copy(alpha = 0.68f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (showBalances) {
                    HeaderCurrency(amount = gold, label = "Vàng", isGem = false)
                    HeaderCurrency(amount = gems, label = "Gem", isGem = true)
                }
                if (showNotifications) {
                    ArcadeHeaderIconButton(onClick = onNotifications) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications > 0) {
                                    Badge {
                                        Text(if (unreadNotifications > 99) "99+" else unreadNotifications.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Thông báo")
                        }
                    }
                }
                actions()
            }
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f))
            )
        }
    }
}

@Composable
fun ArcadeHeaderIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(13.dp),
        color = Color.White.copy(alpha = 0.075f),
        contentColor = ArcadePalette.White
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun HeaderCurrency(amount: Int, label: String, isGem: Boolean) {
    val accent = if (isGem) GemColor else GoldColor
    Surface(
        modifier = Modifier
            .testTag(if (isGem) "header_gem" else "header_gold")
            .semantics { contentDescription = "${formatHeaderAmount(amount)} $label" },
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.08f),
        contentColor = ArcadePalette.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.height(31.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = if (isGem) Icons.Default.Payments else Icons.Default.MonetizationOn,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp)
            )
            Text(
                formatHeaderAmount(amount),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ArcadePalette.White,
                maxLines = 1
            )
        }
    }
}

private fun formatHeaderAmount(amount: Int): String = when {
    amount >= 1_000_000 -> "${amount / 1_000_000}M"
    amount >= 10_000 -> "${amount / 1_000}K"
    else -> amount.toString()
}
