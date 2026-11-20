package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.createPlainTextClipEntry
import com.hienthai.fastowin.platform.rememberTextSharer
import com.hienthai.fastowin.protocol.REFERRAL_INVITER_GOLD
import com.hienthai.fastowin.protocol.REFERRAL_MONTHLY_LIMIT
import com.hienthai.fastowin.protocol.REFERRAL_NEW_PLAYER_GOLD
import com.hienthai.fastowin.protocol.ReferralSummary
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ReferralInviteDialog(
    loadReferralSummary: suspend () -> ReferralSummary,
    shareUrl: (String) -> String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val sharer = rememberTextSharer()
    val scope = rememberCoroutineScope()
    val loadLatest by rememberUpdatedState(loadReferralSummary)
    var summary by remember { mutableStateOf<ReferralSummary?>(null) }
    var failed by remember { mutableStateOf(false) }
    var request by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<TextKey?>(null) }
    LaunchedEffect(request) {
        failed = false
        try {
            summary = loadLatest()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }

    val title = localized(TextKey.ReferralInvite)
    ArcadeDialog(title = title, onDismissRequest = onDismiss, showCloseButton = true, scrollableContent = true) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag("referral_invite_dialog"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val invite = summary
            when {
                failed -> {
                    Text(localized(TextKey.ReferralLoadFailed))
                    ArcadeActionButton(
                        label = localized(TextKey.Retry),
                        onClick = { request++ },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                invite == null -> CircularProgressIndicator()
                else -> {
                    val shareText = localized(TextKey.ReferralShareText,
                        "link" to shareUrl(invite.code), "code" to invite.code)
                    Text(localized(TextKey.ReferralOffer,
                        "friendGold" to REFERRAL_NEW_PLAYER_GOLD,
                        "inviterGold" to REFERRAL_INVITER_GOLD,
                        "limit" to REFERRAL_MONTHLY_LIMIT))
                    Text(localized(TextKey.ReferralCodeLabel), style = MaterialTheme.typography.labelLarge)
                    SelectionContainer {
                        Text(invite.code, style = MaterialTheme.typography.titleLarge)
                    }
                    ArcadeActionButton(
                        label = localized(TextKey.ReferralShare),
                        onClick = {
                            feedback = if (sharer.share(shareText, title).isFailure) TextKey.ShareSheetError else null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ArcadeActionButton(
                        label = localized(TextKey.ReferralCopyCode),
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(createPlainTextClipEntry(invite.code))
                                feedback = TextKey.Copied
                            }
                        },
                        style = ArcadeActionStyle.OUTLINE,
                        modifier = Modifier.fillMaxWidth()
                    )
                    feedback?.let { Text(localized(it), style = MaterialTheme.typography.labelMedium) }
                    Text(localized(TextKey.ReferralPending, "count" to invite.pendingCount))
                    Text(localized(TextKey.ReferralRewarded, "count" to invite.rewardedCount))
                    Text(localized(TextKey.ReferralMonth,
                        "count" to invite.rewardedThisMonth, "limit" to REFERRAL_MONTHLY_LIMIT))
                    if (invite.completedWithoutInviterBonusCount > 0) {
                        Text(localized(TextKey.ReferralCompletedWithoutBonus,
                            "count" to invite.completedWithoutInviterBonusCount))
                    }
                }
            }
        }
    }
}
