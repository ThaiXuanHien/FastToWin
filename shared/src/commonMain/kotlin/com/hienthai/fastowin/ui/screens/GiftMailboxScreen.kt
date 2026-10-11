package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalLocalization
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.giftErrorTextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.GiftState
import com.hienthai.fastowin.protocol.MailboxGift
import com.hienthai.fastowin.state.GiftMailboxState
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeHeaderIconButton
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.GoldColor
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette

@Composable
fun GiftMailboxScreen(
    state: GiftMailboxState,
    isGuest: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onClaim: (String) -> Unit,
    onClaimAll: () -> Unit,
    onUpgradeGuest: () -> Unit,
    nowMillis: Long = epochMillis(),
    modifier: Modifier = Modifier
) {
    SystemBackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("gift_mailbox_screen"),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            FastToWinHeader(
                title = localized(TextKey.GiftMailboxTitle), gold = 0, gems = 0,
                unreadNotifications = 0, onNotifications = {}, onBack = onBack,
                showNotifications = false, showBalances = false,
                actions = {
                    ArcadeHeaderIconButton(onClick = onRefresh, enabled = !isGuest && !state.isLoading && !state.isClaimingAll) {
                        Icon(Icons.Default.Refresh, contentDescription = localized(TextKey.GiftRefresh))
                    }
                }
            )
        }
    ) { paddingValues ->
        ResponsiveScreen(
            modifier = Modifier.padding(paddingValues).windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            ),
            maxContentWidth = 920.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            LazyColumn(
                modifier = contentModifier.fillMaxSize().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "gift_hero") {
                    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Gold500) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Redeem,
                                contentDescription = null,
                                tint = ArcadePalette.Gold500,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                localized(TextKey.GiftMailboxSubtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (isGuest) {
                    item(key = "gift_guest") {
                        ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Gold500) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(localized(TextKey.GiftMailboxGuest), color = MaterialTheme.colorScheme.onSurface)
                                ArcadeActionButton(
                                    label = localized(TextKey.CreateAccount), onClick = onUpgradeGuest,
                                    style = ArcadeActionStyle.GOLD, modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                } else {
                    if (state.canClaimAll) {
                        item(key = "gift_claim_all") {
                            ArcadeActionButton(
                                label = localized(if (state.isClaimingAll) TextKey.GiftClaiming else TextKey.GiftClaimAll),
                                icon = Icons.Default.Redeem,
                                onClick = onClaimAll,
                                enabled = !state.isLoading && !state.isClaimingAll && state.pendingIds.isEmpty(),
                                style = ArcadeActionStyle.GOLD,
                                modifier = Modifier.fillMaxWidth().testTag("gift_claim_all")
                            )
                        }
                    }
                    state.loadError?.let { code ->
                        item(key = "gift_load_error") {
                            Text(localized(giftErrorTextKey(code)), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (state.items.isEmpty() && !state.isLoading && state.loadError == null) {
                        item(key = "gift_empty") {
                            Text(localized(TextKey.GiftMailboxEmpty), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    items(state.items, key = MailboxGift::campaignId) { gift ->
                        GiftCard(
                            gift = gift,
                            errorCode = state.itemErrors[gift.campaignId],
                            isPending = gift.campaignId in state.pendingIds || state.isClaimingAll,
                            onClaim = { onClaim(gift.campaignId) },
                            nowMillis = nowMillis
                        )
                    }
                    if (state.isLoading) {
                        item(key = "gift_loading") {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (state.hasMore) {
                        item(key = "gift_more") {
                            ArcadeActionButton(
                                label = localized(TextKey.GiftLoadMore), onClick = onLoadMore,
                                style = ArcadeActionStyle.OUTLINE,
                                enabled = !state.isClaimingAll,
                                modifier = Modifier.fillMaxWidth().testTag("gift_load_more")
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun GiftCard(gift: MailboxGift, errorCode: String?, isPending: Boolean, onClaim: () -> Unit, nowMillis: Long) {
    val language = LocalLocalization.current.language
    val isVietnamese = language == AppLanguage.VIETNAMESE
    ArcadePanel(modifier = Modifier.fillMaxWidth().testTag("gift_${gift.campaignId}"), accent = ArcadePalette.Gold500) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            gift.compensation?.let { context ->
                Text(
                    localized(TextKey.GiftCompensationRound, "incident" to context.incidentKey, "round" to context.roundNumber),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("gift_compensation_${gift.campaignId}")
                )
            }
            Text(
                if (isVietnamese) gift.titleVi else gift.titleEn,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            GiftRewardAmounts(gift.gold, gift.xp)
            Text(if (isVietnamese) gift.bodyVi else gift.bodyEn, color = MaterialTheme.colorScheme.onSurface)
            val status = when (gift.state) {
                GiftState.AVAILABLE -> {
                    val hours = ((gift.expiresAtEpochMillis - nowMillis).coerceAtLeast(0) + 3_599_999) / 3_600_000
                    if (hours > 48) localized(TextKey.GiftExpiresInDays, "count" to ((hours + 23) / 24))
                    else localized(TextKey.GiftExpiresInHours, "count" to hours)
                }
                GiftState.CLAIMED -> localized(TextKey.GiftClaimed)
                GiftState.EXPIRED -> localized(TextKey.GiftExpired)
                GiftState.CANCELLED -> localized(TextKey.GiftCancelled)
            }
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            errorCode?.let { Text(localized(giftErrorTextKey(it)), color = MaterialTheme.colorScheme.error) }
            if (gift.state == GiftState.AVAILABLE) {
                ArcadeActionButton(
                    label = localized(if (isPending) TextKey.GiftClaiming else TextKey.GiftClaim),
                    onClick = onClaim,
                    enabled = !isPending,
                    style = ArcadeActionStyle.GOLD,
                    modifier = Modifier.fillMaxWidth().testTag("gift_claim_${gift.campaignId}")
                )
            }
        }
    }
}

/** Stored per-gift amounts, including zero; currency names remain in localized semantics. */
@Composable
private fun GiftRewardAmounts(gold: Int, xp: Int) {
    val description = listOf(
        localized(TextKey.GiftGoldReward, "count" to gold),
        localized(TextKey.GiftXpReward, "count" to xp)
    ).joinToString(", ")
    FlowRow(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Same filled coin and tint as Mission, on the shared navy backing.
            Surface(shape = CircleShape, color = ArcadePalette.Navy900) {
                Icon(Icons.Default.MonetizationOn, null, tint = GoldColor, modifier = Modifier.padding(2.dp).size(20.dp))
            }
            Text(gold.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(xp.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
