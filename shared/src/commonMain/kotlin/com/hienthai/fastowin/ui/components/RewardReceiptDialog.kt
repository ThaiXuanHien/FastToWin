package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalLocalization
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.ui.theme.ArcadePalette

/** Granted amounts for one acknowledged action; callers own receipt identity and consumption. */
data class RewardReceiptAmounts(val gold: Long = 0, val xp: Long = 0, val gems: Long = 0, val plays: Long = 0)

@Composable
fun RewardReceiptDialog(
    reward: RewardReceiptAmounts,
    onDismiss: () -> Unit,
    title: String = localized(TextKey.RewardsReceived),
    subtitle: String? = null,
    xpLabel: String = "XP"
) {
    ArcadeDialog(
        title = title,
        onDismissRequest = onDismiss,
        subtitle = subtitle,
        modifier = Modifier.testTag("reward_receipt_dialog"),
        scrollableContent = true,
        fixedActions = {
            ArcadeActionButton(localized(TextKey.Close), onDismiss,
                modifier = Modifier.fillMaxWidth().testTag("reward_receipt_close"),
                style = ArcadeActionStyle.GOLD)
        }
    ) {
        val groupSeparator = if (LocalLocalization.current.language == AppLanguage.VIETNAMESE) "." else ","
        val rows = listOf(
            Triple("gold", TextKey.Gold, reward.gold),
            Triple("xp", null, reward.xp),
            Triple("gems", TextKey.Gems, reward.gems),
            Triple("plays", TextKey.BankedPlays, reward.plays)
        )
        rows.filter { it.third > 0 }.forEach { (kind, label, amount) ->
            ArcadePanel(Modifier.fillMaxWidth().testTag("reward_receipt_$kind"), accent = ArcadePalette.Gold400) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(when (kind) {
                        "gold" -> Icons.Default.MonetizationOn
                        "xp" -> Icons.Default.Star
                        "gems" -> Icons.Default.Payments
                        else -> Icons.Default.Redeem
                    }, contentDescription = null, tint = ArcadePalette.Gold400, modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label?.let { localized(it) } ?: xpLabel, style = MaterialTheme.typography.bodyMedium)
                        Text("+" + amount.toString().reversed().chunked(3).joinToString(groupSeparator).reversed(),
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black,
                            color = ArcadePalette.Mint400)
                    }
                }
            }
        }
    }
}
