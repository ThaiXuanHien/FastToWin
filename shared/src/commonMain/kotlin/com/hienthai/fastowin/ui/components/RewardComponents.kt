package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val GoldColor = Color(0xFFFFB300)
val GemColor = Color(0xFF00A86B)

@Composable
fun WalletBalanceRow(
    gold: Int,
    gems: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.semantics {
            contentDescription = "$gold vàng, $gems gem"
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CurrencyPill(
            amount = gold,
            label = "Vàng",
            icon = { Icon(Icons.Default.MonetizationOn, null, tint = GoldColor, modifier = Modifier.size(20.dp)) }
        )
        CurrencyPill(
            amount = gems,
            label = "Gem",
            icon = { Icon(Icons.Default.Payments, null, tint = GemColor, modifier = Modifier.size(20.dp)) }
        )
    }
}

@Composable
fun RewardAmounts(
    gold: Int,
    xp: Int,
    gems: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.semantics {
            contentDescription = rewardDescription(gold, xp, gems)
        },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (gold > 0) RewardAmount(
            amount = gold,
            suffix = "",
            icon = { Icon(Icons.Default.MonetizationOn, null, tint = GoldColor, modifier = Modifier.size(17.dp)) }
        )
        if (xp > 0) RewardAmount(
            amount = xp,
            suffix = " XP",
            icon = { Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp)) }
        )
        if (gems > 0) RewardAmount(
            amount = gems,
            suffix = "",
            icon = { Icon(Icons.Default.Payments, null, tint = GemColor, modifier = Modifier.size(17.dp)) }
        )
    }
}

@Composable
private fun CurrencyPill(
    amount: Int,
    label: String,
    icon: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Text(amount.toString(), fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RewardAmount(
    amount: Int,
    suffix: String,
    icon: @Composable () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text("+$amount$suffix", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

private fun rewardDescription(gold: Int, xp: Int, gems: Int): String = buildList {
    if (gold > 0) add("$gold vàng")
    if (xp > 0) add("$xp XP")
    if (gems > 0) add("$gems gem")
}.joinToString()
