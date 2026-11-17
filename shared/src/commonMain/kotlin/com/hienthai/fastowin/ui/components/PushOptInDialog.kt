package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.ui.theme.ArcadePalette

/**
 * Explains what notifications are for before the system permission prompt, so the player
 * decides with context. "Later" never opens the system prompt; Settings keeps its toggle.
 */
@Composable
fun PushOptInDialog(
    onEnable: () -> Unit,
    onLater: () -> Unit
) {
    ArcadeDialog(
        title = localized(TextKey.PushOptInTitle),
        subtitle = localized(TextKey.PushOptInSubtitle),
        onDismissRequest = onLater
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArcadePanel(
                modifier = Modifier.fillMaxWidth(),
                accent = ArcadePalette.Gold500
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PushOptInBenefit(
                        icon = Icons.Rounded.GroupAdd,
                        tint = ArcadePalette.Mint400,
                        text = localized(TextKey.PushOptInRoomInvites)
                    )
                    PushOptInBenefit(
                        icon = Icons.Rounded.EmojiEvents,
                        tint = ArcadePalette.Gold500,
                        text = localized(TextKey.PushOptInRewards)
                    )
                }
            }
            Text(
                text = localized(TextKey.PushOptInPrivacy),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA9BADC),
                modifier = Modifier.fillMaxWidth()
            )
            ArcadeActionButton(
                label = localized(TextKey.PushOptInEnable),
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.NotificationsActive,
                style = ArcadeActionStyle.GOLD
            )
            ArcadeActionButton(
                label = localized(TextKey.Later),
                onClick = onLater,
                modifier = Modifier.fillMaxWidth(),
                style = ArcadeActionStyle.OUTLINE
            )
        }
    }
}

@Composable
private fun PushOptInBenefit(icon: ImageVector, tint: Color, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    Brush.linearGradient(listOf(ArcadePalette.Blue700, ArcadePalette.Violet600)),
                    RoundedCornerShape(12.dp)
                )
                .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
    }
}
