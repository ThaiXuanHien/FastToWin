package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.state.AppNotification
import com.hienthai.fastowin.state.AppNotificationKind
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.ui.theme.ArcadePalette

@Composable
fun ProgressionUnlockDialog(notification: AppNotification, onDismiss: () -> Unit) {
    ArcadeDialog(
        title = notification.title,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("progression_unlock_dialog"),
        fixedActions = {
            ArcadeActionButton(
                label = localized(TextKey.Close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().testTag("progression_unlock_close"),
                style = ArcadeActionStyle.GOLD
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = if (notification.kind == AppNotificationKind.ACHIEVEMENT) {
                    Icons.Default.EmojiEvents
                } else {
                    Icons.Default.AutoAwesome
                },
                contentDescription = null,
                tint = ArcadePalette.Gold400
            )
            Text(
                text = notification.message,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            val rewards = listOf(
                Triple("gold", localized(TextKey.Gold), notification.rewardGold),
                Triple("xp", "XP", notification.rewardXp),
                Triple("gems", localized(TextKey.Gems), notification.rewardGems)
            ).filter { it.third > 0 }
            rewards.forEach { (kind, label, amount) ->
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("progression_unlock_$kind"),
                    color = ArcadePalette.Navy800,
                    shape = MaterialTheme.shapes.medium
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = when (kind) {
                                "gold" -> Icons.Default.MonetizationOn
                                "gems" -> Icons.Default.Payments
                                else -> Icons.Default.Star
                            },
                            contentDescription = null,
                            tint = ArcadePalette.Gold400
                        )
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "+$amount",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = ArcadePalette.Mint400
                            )
                        }
                    }
                }
            }
        }
    }
}
