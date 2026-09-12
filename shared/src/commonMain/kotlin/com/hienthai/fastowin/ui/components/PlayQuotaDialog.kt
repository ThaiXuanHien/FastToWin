package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.RewardedAdGatewayState
import com.hienthai.fastowin.protocol.PlayQuotaSnapshot

@Composable
fun PlayQuotaExhaustedDialog(
    quota: PlayQuotaSnapshot?,
    rewardedAdState: RewardedAdGatewayState,
    isClaiming: Boolean,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit
) {
    ArcadeDialog(
        title = localized(TextKey.OnlineQuotaExhaustedTitle).uppercase(),
        subtitle = localized(TextKey.OnlineQuotaExhaustedMessage),
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("play_quota_dialog")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = localized(
                    TextKey.OnlineMatchesRemaining,
                    "remaining" to (quota?.remainingMatches ?: 0),
                    "total" to ((quota?.baseMatches ?: 10) + (quota?.bonusMatchesGranted ?: 0))
                ),
                modifier = Modifier.fillMaxWidth().testTag("online_quota_dialog_remaining"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            if (rewardedAdState.isReady) {
                ArcadeActionButton(
                    label = localized(TextKey.WatchAdForTwoMatches),
                    onClick = onWatchAd,
                    enabled = !rewardedAdState.isLoading && !isClaiming,
                    modifier = Modifier.fillMaxWidth().testTag("watch_rewarded_ad")
                )
            } else {
                Text(
                    text = rewardedAdState.error?.let { localized(it) }
                        ?: localized(TextKey.RewardedAdUnavailable),
                    modifier = Modifier.fillMaxWidth().testTag("rewarded_ad_unavailable"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA9BADC)
                )
            }

            ArcadeActionButton(
                label = localized(TextKey.Close).uppercase(),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().testTag("play_quota_close"),
                style = ArcadeActionStyle.OUTLINE
            )
        }
    }
}
