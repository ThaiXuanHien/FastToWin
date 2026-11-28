package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalLocalization
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.giftErrorTextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.GiftState
import com.hienthai.fastowin.protocol.MailboxGift
import com.hienthai.fastowin.state.GiftMailboxState
import com.hienthai.fastowin.state.stateAt
import com.hienthai.fastowin.ui.components.A1CoinIcon
import com.hienthai.fastowin.ui.components.A1GemIcon
import com.hienthai.fastowin.ui.components.A1Tag
import com.hienthai.fastowin.ui.components.A1TagTone
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.FastToWinPullRefresh
import com.hienthai.fastowin.ui.components.RewardReceiptAmounts
import com.hienthai.fastowin.ui.components.RewardReceiptDialog
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.format.fullNumber
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.A1Palette
import kotlinx.coroutines.delay

@Composable
fun GiftMailboxScreen(
    state: GiftMailboxState,
    isGuest: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onClaim: (String) -> Unit,
    onClaimAll: () -> Unit,
    onDismissClaimedGift: () -> Unit = {},
    onUpgradeGuest: () -> Unit = {},
    nowMillis: Long? = null,
    modifier: Modifier = Modifier
) {
    SystemBackHandler(onBack = onBack)
    val serverClock = state.serverClock
    var observedTime by remember(serverClock) { mutableStateOf(serverClock?.now()) }
    LaunchedEffect(serverClock, state.items, nowMillis) {
        if (serverClock == null || nowMillis != null) return@LaunchedEffect
        while (true) {
            val now = serverClock.now()
            observedTime = now
            val nextExpiry = state.items.asSequence()
                .filter { it.stateAt(now) == GiftState.AVAILABLE }
                .minOfOrNull { it.expiresAtEpochMillis } ?: break
            delay(minOf(nextExpiry - now, 60_000L))
        }
    }
    val deadlineClock = nowMillis ?: observedTime
    val displayNow = deadlineClock ?: epochMillis()
    val claimedReward = when {
        state.lastClaimedBatch.isNotEmpty() -> RewardReceiptAmounts(
            gold = state.lastClaimedBatch.sumOf { it.awardedGold.toLong() },
            xp = state.lastClaimedBatch.sumOf { it.awardedXp.toLong() },
            gems = state.lastClaimedBatch.sumOf { it.awardedGems.toLong() },
            plays = state.lastClaimedBatch.sumOf { it.gift.plays.toLong() }
        )
        state.lastClaimedGift != null -> RewardReceiptAmounts(
            gold = state.lastClaimedGift.awardedGold.toLong(),
            xp = state.lastClaimedGift.awardedXp.toLong(),
            gems = state.lastClaimedGift.awardedGems.toLong(),
            plays = state.lastClaimedGift.gift.plays.toLong()
        )
        else -> null
    }
    claimedReward?.let { reward ->
        RewardReceiptDialog(
            reward,
            onDismiss = onDismissClaimedGift
        )
    }
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("gift_mailbox_screen"),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            // Như thiết kế: chỉ nút quay lại + tiêu đề; tải lại bằng cách kéo xuống.
            FastToWinHeader(
                title = localized(TextKey.GiftMailboxShortTitle), gold = 0, gems = 0,
                unreadNotifications = 0, onNotifications = {}, onBack = onBack,
                showNotifications = false, showBalances = false
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
            var tab by rememberSaveable { mutableStateOf(0) }
            val unclaimed = state.items.filter { it.stateAt(deadlineClock) != GiftState.CLAIMED }
            val claimed = state.items.filter { it.stateAt(deadlineClock) == GiftState.CLAIMED }
            val availableNow = state.items.count { it.stateAt(deadlineClock) == GiftState.AVAILABLE }
            val shown = if (tab == 0) unclaimed else claimed
            FastToWinPullRefresh(
                isRefreshing = state.isLoading && state.items.isNotEmpty(),
                onRefresh = { if (!isGuest && !state.isLoading && !state.isClaimingAll) onRefresh() },
                modifier = contentModifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isGuest) {
                        item(key = "gift_guest") {
                            Column(
                                Modifier.fillMaxWidth().a1Card().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(localized(TextKey.GiftMailboxGuest), color = A1Palette.Ink, fontWeight = FontWeight.SemiBold)
                                ArcadeActionButton(
                                    label = localized(TextKey.CreateAccount), onClick = onUpgradeGuest,
                                    style = ArcadeActionStyle.GOLD, modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        return@LazyColumn
                    }
                    item(key = "gift_tabs") {
                        ArcadeSegmentedControl(
                            labels = listOf(
                                maxOf(availableNow.toLong(), state.availableCount).let { count ->
                                    if (count > 0) localized(TextKey.GiftTabUnclaimed, "count" to count) else localized(TextKey.GiftTabUnclaimedEmpty)
                                },
                                localized(TextKey.GiftClaimed)
                            ),
                            selectedIndex = tab,
                            onSelected = { tab = it },
                            modifier = Modifier.testTag("gift_tabs"),
                            itemTestTag = { if (it == 0) "gift_tab_unclaimed" else "gift_tab_claimed" }
                        )
                    }
                    state.loadError?.let { code ->
                        item(key = "gift_load_error") {
                            Text(localized(giftErrorTextKey(code)), color = A1Palette.RedText, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (shown.isEmpty() && !state.isLoading && state.loadError == null) {
                        item(key = "gift_empty") {
                            Text(
                                localized(TextKey.GiftMailboxEmpty),
                                modifier = Modifier.fillMaxWidth().a1Card().padding(horizontal = 18.dp, vertical = 16.dp),
                                color = A1Palette.Muted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    items(shown, key = MailboxGift::campaignId) { gift ->
                        GiftCard(
                            gift = gift,
                            displayState = gift.stateAt(deadlineClock),
                            errorCode = state.itemErrors[gift.campaignId],
                            isPending = gift.campaignId in state.pendingIds || state.isClaimingAll,
                            onClaim = { onClaim(gift.campaignId) },
                            nowMillis = displayNow
                        )
                    }
                    if (tab == 0 && (state.hasMore || availableNow > 0)) {
                        item(key = "gift_claim_all") {
                            ArcadeActionButton(
                                label = localized(if (state.isClaimingAll) TextKey.GiftClaiming else TextKey.GiftClaimAll).uppercase(),
                                onClick = onClaimAll,
                                enabled = !state.isLoading && !state.isClaimingAll && state.pendingIds.isEmpty(),
                                style = ArcadeActionStyle.GOLD,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("gift_claim_all")
                            )
                        }
                    }
                    if (state.isLoading) {
                        item(key = "gift_loading") {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
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
            }
        }
    }
}

/** Ô icon của quà: đền bù/quà admin xanh lá, thưởng đua bang tím. */
private fun giftTile(gift: MailboxGift): Pair<ImageVector, androidx.compose.ui.graphics.Color> = when {
    gift.clanRace != null -> Icons.Rounded.EmojiEvents to A1Palette.Purple2
    gift.compensation != null -> Icons.Rounded.Redeem to A1Palette.Green2
    gift.gems > 0 -> Icons.Rounded.WorkspacePremium to A1Palette.Purple2
    else -> Icons.Rounded.Redeem to A1Palette.Green2
}

/** Thẻ quà (".card.gf"): ô icon 52dp, tên quà, dòng phụ + hạn nhận, phần thưởng và nút Nhận xanh lá. */
@Composable
private fun GiftCard(
    gift: MailboxGift,
    displayState: GiftState,
    errorCode: String?,
    isPending: Boolean,
    onClaim: () -> Unit,
    nowMillis: Long
) {
    val isVietnamese = LocalLocalization.current.language == AppLanguage.VIETNAMESE
    val race = gift.clanRace
    val (icon, tileColor) = giftTile(gift)
    val status = when (displayState) {
        GiftState.AVAILABLE -> {
            val hours = (((race?.expiresAtEpochMillis ?: gift.expiresAtEpochMillis) - nowMillis).coerceAtLeast(0) + 3_599_999) / 3_600_000
            when {
                hours > 48 -> localized(TextKey.GiftExpiresInDays, "count" to ((hours + 23) / 24))
                hours > 0 -> localized(TextKey.GiftExpiresInHours, "count" to hours)
                else -> null
            }
        }
        // Hết hạn / đã hủy hiện thành nhãn bên phải thẻ, thay chỗ nút Nhận.
        GiftState.CLAIMED, GiftState.EXPIRED, GiftState.CANCELLED -> null
    }
    val subtitle = if (race != null) localized(TextKey.ClanRaceRankPoints, "rank" to race.finalRank, "points" to race.points)
        else if (isVietnamese) gift.bodyVi else gift.bodyEn
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("gift_${gift.campaignId}")
            .a1Card()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(52.dp)
                .background(tileColor, RoundedCornerShape(14.dp))
                .border(2.dp, A1Palette.Line, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            gift.compensation?.let { context ->
                Text(
                    localized(TextKey.GiftCompensationRound, "incident" to context.incidentKey, "round" to context.roundNumber),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = A1Palette.Blue2,
                    modifier = Modifier.testTag("gift_compensation_${gift.campaignId}")
                )
            }
            Text(
                if (race != null) localized(TextKey.ClanRaceGift, "week" to race.weekStart, "clan" to race.clanName, "rank" to race.finalRank)
                else if (isVietnamese) gift.titleVi else gift.titleEn,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 21.sp),
                fontWeight = FontWeight.ExtraBold,
                color = A1Palette.Ink
            )
            val line = listOfNotNull(subtitle.takeIf { it.isNotBlank() }, status?.let { s -> if (subtitle.isBlank()) s else s.replaceFirstChar { it.lowercase() } })
                .joinToString(" · ")
            if (line.isNotBlank()) {
                Text(
                    line,
                    modifier = if (race != null) Modifier.testTag("gift_race_deadline_${gift.campaignId}") else Modifier,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = A1Palette.Muted
                )
            }
            if (race != null) {
                GiftRewardAmounts(race.reward.gold, race.reward.xp, race.reward.gems, plays = 0)
            } else {
                GiftRewardAmounts(gift.gold, gift.xp, gift.gems, gift.plays)
            }
            errorCode?.let { Text(localized(giftErrorTextKey(it)), color = A1Palette.RedText, fontWeight = FontWeight.SemiBold) }
        }
        when (displayState) {
            GiftState.CLAIMED -> ArcadeActionButton(
                label = localized(TextKey.GiftClaimed),
                onClick = {},
                enabled = false,
                style = ArcadeActionStyle.OUTLINE,
                compact = true,
                modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 72.dp, max = 140.dp).testTag("gift_claim_${gift.campaignId}")
            )
            GiftState.AVAILABLE -> ArcadeActionButton(
                label = localized(if (isPending) TextKey.GiftClaiming else TextKey.GiftClaim),
                onClick = onClaim,
                enabled = !isPending,
                style = ArcadeActionStyle.SUCCESS,
                compact = true,
                modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 72.dp, max = 140.dp).testTag("gift_claim_${gift.campaignId}")
            )
            GiftState.EXPIRED -> A1Tag(localized(TextKey.GiftExpired), A1TagTone.RED)
            GiftState.CANCELLED -> A1Tag(localized(TextKey.GiftCancelled), A1TagTone.BLUE)
        }
    }
}

/** Phần thưởng của quà (".rw"): đồng vàng, Gem, XP, lượt chơi; trình đọc màn hình đọc đủ tên tiền. */
@Composable
private fun GiftRewardAmounts(gold: Int, xp: Int, gems: Int, plays: Int) {
    val description = buildList {
        add(localized(TextKey.GiftGoldReward, "count" to gold))
        add(localized(TextKey.GiftXpReward, "count" to xp))
        if (gems > 0) add(localized(TextKey.RewardGemsDescription, "count" to gems))
        if (plays > 0) add(localized(TextKey.PlayCreditOffer, "plays" to plays))
    }.joinToString(", ")
    val amountStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (gold > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            A1CoinIcon(18.dp)
            Text(fullNumber(gold.toLong()), style = amountStyle, fontWeight = FontWeight.ExtraBold, color = A1Palette.Ink)
        }
        if (gems > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            A1GemIcon(16.dp)
            Text(fullNumber(gems.toLong()), style = amountStyle, fontWeight = FontWeight.ExtraBold, color = A1Palette.Ink)
        }
        if (xp > 0) Text("+" + localized(TextKey.GiftXpReward, "count" to xp), style = amountStyle, fontWeight = FontWeight.ExtraBold, color = A1Palette.Blue2)
        if (plays > 0) Text(
            localized(TextKey.PlayCreditOffer, "plays" to plays),
            style = amountStyle,
            fontWeight = FontWeight.ExtraBold,
            color = A1Palette.Ink
        )
    }
}
