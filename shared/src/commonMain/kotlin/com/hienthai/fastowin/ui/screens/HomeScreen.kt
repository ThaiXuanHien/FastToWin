package com.hienthai.fastowin.ui.screens
import com.hienthai.fastowin.ui.components.LocalNewOnlineIntentAllowed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.network.toAvatarImageUrl
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.localization.localizedNetworkText
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.LeaderboardSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.PlayQuotaSnapshot
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.RewardReceiptAmounts
import com.hienthai.fastowin.ui.components.RewardReceiptDialog
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.CrossedSwordsIcon
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.theme.A1Palette
import com.hienthai.fastowin.ui.components.A1Dimens
import com.hienthai.fastowin.ui.components.TiaMascot
import com.hienthai.fastowin.ui.components.TiaMood
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.components.A1Badge
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Person
import com.hienthai.fastowin.ui.components.a1Hills
import com.hienthai.fastowin.ui.components.drawSunnySky
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.toSize
import com.hienthai.fastowin.ui.components.A1OutlinedText
import com.hienthai.fastowin.ui.format.fullNumber
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.delay

internal enum class MainTab { HOME, ROOMS, LEADERBOARD, CLAN, ACCOUNT }

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HomeDashboard(
    state: GameState,
    isGuest: Boolean,
    serverUrl: String = "",
    onQuickMatch: (GameMode, MatchType) -> Unit,
    onOpenRooms: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenGifts: () -> Unit,
    unclaimedGiftCount: Long = 0,
    rewardEventState: com.hienthai.fastowin.state.RewardEventUiState? = null,
    announcementState: com.hienthai.fastowin.state.AnnouncementState? = null,
    onOpenAnnouncement: (String) -> Unit = {},
    onHideAnnouncement: (String) -> Unit = {},
    onOpenRewardEvents: () -> Unit = {},
    onRefreshRewardEvents: () -> Unit = {},
    onOpenClan: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenTournament: () -> Unit,
    onOpenShop: () -> Unit,
    onClaimDailyCheckIn: () -> Unit,
    onDismissDailyCheckInReward: () -> Unit = {},
    onUpgradeGuest: () -> Unit,
    onLogout: () -> Unit,
    onPlayQuotaExhausted: () -> Unit = {},
    onOpenMissions: () -> Unit = {},
    claimableMissionCount: Int = 0,
    onOpenCheckInDetails: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val profile = state.profile
    state.dailyCheckInResult?.let { result ->
        RewardReceiptDialog(
            RewardReceiptAmounts(
                gold = result.rewardGold.toLong(),
                xp = result.rewardXp.toLong(),
                gems = result.rewardGems.toLong()
            ),
            onDismiss = onDismissDailyCheckInReward
        )
    }
    val displayName = profile?.displayName ?: state.player.name
    val avatarId = profile?.avatarId ?: state.player.avatarId
    val avatarImageUrl = profile?.userId?.let { userId ->
        serverUrl.toAvatarImageUrl(userId, state.avatarRevision)
    }.orEmpty()
    val equippedFrameId = profile?.progression?.cosmetics?.firstOrNull {
        it.type == CosmeticType.FRAME && it.equipped
    }?.id ?: state.player.frameId
    val elo = profile?.statistics?.eloRating
    val rank = state.leaderboard?.homeRank()
    val onlineFriends = state.social.friends.count { it.presence != FriendPresence.OFFLINE }
    val openSocial = if (isGuest) onUpgradeGuest else onOpenFriends
    var showMatchTypePicker by remember { mutableStateOf(false) }
    var pendingMatchType by remember { mutableStateOf<MatchType?>(null) }
    if (showMatchTypePicker) {
        MatchTypePickerDialog(
            title = localized(TextKey.ChooseMatchType),
            quota = state.playQuota,
            onQuotaExhausted = {
                showMatchTypePicker = false
                onPlayQuotaExhausted()
            },
            onDismiss = { showMatchTypePicker = false },
            onSelect = { matchType ->
                showMatchTypePicker = false
                pendingMatchType = matchType
            }
        )
    }
    pendingMatchType?.let { matchType ->
        GameModePickerDialog(
            title = if (matchType == MatchType.RANKED) {
                localized(TextKey.ChooseRankedMode)
            } else {
                localized(TextKey.ChooseCasualMode)
            },
            playerLevel = profile?.progression?.level ?: 1,
            onDismiss = { pendingMatchType = null },
            onSelect = { mode ->
                pendingMatchType = null
                onQuickMatch(mode, matchType)
            }
        )
    }

    var showCheckIn by remember { mutableStateOf(false) }
    if (showCheckIn && profile != null) {
        ArcadeDialog(
            title = localized(TextKey.CheckIn),
            onDismissRequest = { showCheckIn = false },
            showCloseButton = true,
            scrollableContent = true
        ) {
            DailyCheckInContent(
                checkIn = profile.progression.dailyCheckIn,
                isClaiming = state.isDailyCheckInClaiming,
                onClaim = onClaimDailyCheckIn,
                onOpenDetails = onOpenCheckInDetails
            )
        }
    }
    val newOnlineIntentAllowed = LocalNewOnlineIntentAllowed.current
    val signedIn = !isGuest && profile != null

    BoxWithConstraints(modifier = modifier.fillMaxSize().testTag("home_screen")) {
        val largeText = LocalDensity.current.fontScale >= 1.3f
        val narrow = largeText || maxWidth < 280.dp
        val compactWidth = maxWidth < 320.dp
        val bestScore = profile?.statistics?.highestScore ?: 0
        // Bong bóng linh vật: nhắc kỷ lục để phá; chưa có kỷ lục thì chào người chơi.
        val greeting = if (bestScore > 0) {
            localized(TextKey.HomeBestScore, "score" to fullNumber(bestScore.toLong())) + "\n" + localized(TextKey.HomeBeatIt)
        } else {
            localized(TextKey.GreetingPlayer, "player" to displayName) + "\n" + localized(TextKey.ReadyForRecord)
        }
        val activeEvents = rewardEventState?.items?.size ?: 0
        // Vị trí khu linh vật trong nội dung cuộn, để vẽ trời nắng phía SAU mọi thẻ và nút.
        var sceneArea by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .a1Hills()
                .verticalScroll(rememberScrollState())
                .drawBehind { sceneArea?.let { drawSunnySky(it, rayCenterYFraction = 0.42f) } },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            ArcadePlayerSummary(
                displayName = displayName,
                avatarId = avatarId,
                avatarImageUrl = avatarImageUrl,
                frameId = equippedFrameId,
                level = profile?.progression?.level ?: 1,
                currentXp = profile?.progression?.currentLevelExperience ?: 0,
                nextXp = profile?.progression?.nextLevelExperience ?: 100,
                elo = elo,
                tier = profile?.progression?.season?.tier,
                onOpenProfile = if (isGuest) onUpgradeGuest else onOpenProfile
            )

            announcementState?.banner?.let { announcement ->
                com.hienthai.fastowin.ui.components.AnnouncementBanner(announcement, announcementState.pending,
                    { onOpenAnnouncement(announcement.announcement.id) }, { onHideAnnouncement(announcement.announcement.id) })
            }
            // A1: sự kiện thưởng mở từ ô "Sự kiện" cạnh linh vật (có huy hiệu số sự kiện), không dùng banner.

            // Khu vực giữa: lối tắt hai bên và linh vật Tia ở giữa (theo thiết kế A1).
            val leftShortcuts: @Composable () -> Unit = {
                HomeSideShortcut(
                    label = localized(TextKey.HomeEvents),
                    icon = Icons.Default.Celebration,
                    tint = Color(0xFFE0401A),
                    onClick = onOpenRewardEvents,
                    highlight = true,
                    badgeText = if (activeEvents > 0) activeEvents.toString() else null,
                    badgeColor = A1Palette.Purple2,
                    modifier = Modifier.testTag("home_events")
                )
                HomeSideShortcut(
                    label = localized(TextKey.HomeGifts),
                    icon = Icons.Default.Mail,
                    tint = A1Palette.Purple2,
                    onClick = onOpenGifts,
                    badgeCount = if (isGuest) 0 else unclaimedGiftCount,
                    modifier = Modifier.testTag("home_gift_mailbox")
                )
            }
            val rightShortcuts: @Composable () -> Unit = {
                if (signedIn) {
                    HomeSideShortcut(
                        label = localized(TextKey.CheckIn),
                        icon = Icons.Default.CalendarMonth,
                        tint = A1Palette.Blue2,
                        onClick = { showCheckIn = true },
                        badgeText = if (profile?.progression?.dailyCheckIn?.claimedToday == false) "!" else null,
                        modifier = Modifier.testTag("home_check_in")
                    )
                    HomeSideShortcut(
                        label = localized(TextKey.MissionsTitle),
                        icon = Icons.Default.TaskAlt,
                        tint = A1Palette.Green2,
                        onClick = onOpenMissions,
                        badgeCount = claimableMissionCount.toLong(),
                        badgeTextKey = TextKey.ClaimableMissionCount,
                        modifier = Modifier.testTag("home_missions")
                    )
                }
                HomeSideShortcut(
                    label = localized(TextKey.HomeTournament),
                    icon = Icons.Default.EmojiEvents,
                    tint = Color(0xFFE09A00),
                    onClick = if (isGuest) onUpgradeGuest else onOpenTournament,
                    modifier = Modifier.testTag("home_tournament")
                )
            }
            if (narrow) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        leftShortcuts()
                        rightShortcuts()
                    }
                    HomeMascot(greeting, mascotSize = 96.dp)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp)
                        .onGloballyPositioned { sceneArea = androidx.compose.ui.geometry.Rect(it.positionInParent(), it.size.toSize()) },
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { leftShortcuts() }
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 280.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        HomeMascot(greeting, mascotSize = if (compactWidth) 110.dp else 140.dp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { rightShortcuts() }
                }
            }

            ArcadeActionButton(
                label = localized(if (isGuest) TextKey.LoginToPlay else TextKey.PlayNow),
                style = ArcadeActionStyle.GOLD,
                hero = true,
                onClick = if (isGuest) onUpgradeGuest else ({ showMatchTypePicker = true }),
                enabled = newOnlineIntentAllowed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag("home_quick_match")
            )
            val createRoom: @Composable (Modifier) -> Unit = { m ->
                ArcadeActionButton(
                    label = localized(TextKey.CreateRoom),
                    icon = Icons.Default.AddCircle,
                    style = ArcadeActionStyle.PRIMARY,
                    onClick = onOpenRooms,
                    enabled = newOnlineIntentAllowed,
                    modifier = m.testTag("home_action:create_room")
                )
            }
            val joinRoom: @Composable (Modifier) -> Unit = { m ->
                ArcadeActionButton(
                    label = localized(TextKey.HomeJoinRoom),
                    icon = Icons.AutoMirrored.Filled.Login,
                    style = ArcadeActionStyle.SPECIAL,
                    onClick = onOpenRooms,
                    enabled = newOnlineIntentAllowed,
                    modifier = m.testTag("home_action:join_room")
                )
            }
            if (narrow || compactWidth) {
                createRoom(Modifier.fillMaxWidth())
                joinRoom(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    createRoom(Modifier.weight(1f))
                    joinRoom(Modifier.weight(1f))
                }
            }

            HomeQuickAction(
                localized(TextKey.OfflinePracticeTitle),
                localized(TextKey.OfflinePracticeDescription),
                Icons.Default.FitnessCenter,
                A1Palette.Green2,
                onOpenPractice,
                Modifier.fillMaxWidth()
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (isGuest) {
                ArcadePanel(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(localized(TextKey.SaveProgress), style = MaterialTheme.typography.titleMedium)
                        Text(
                            localized(TextKey.SaveProgressDescription),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ArcadeActionButton(
                            label = localized(TextKey.CreateAccount),
                            onClick = onUpgradeGuest,
                            modifier = Modifier.fillMaxWidth(),
                            style = ArcadeActionStyle.GOLD
                        )
                        ArcadeActionButton(
                            label = localized(TextKey.ExitGuestMode),
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth(),
                            style = ArcadeActionStyle.OUTLINE
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Linh vật Tia đứng giữa trang chủ cùng bong bóng lời thoại. */
@Composable
private fun HomeMascot(message: String, mascotSize: androidx.compose.ui.unit.Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = A1Palette.Ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .testTag("home_mascot_bubble")
                .widthIn(max = 220.dp)
                .a1Card(radius = 18.dp, depth = 4.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
        TiaMascot(TiaMood.CHEER, size = mascotSize)
    }
}

/** Ô lối tắt bên cạnh linh vật: hộp trắng 60dp viền navy + nhãn nền navy. */
@Composable
private fun HomeSideShortcut(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Long = 0,
    badgeText: String? = null,
    badgeTextKey: TextKey = TextKey.GiftUnclaimedCount,
    highlight: Boolean = false,
    badgeColor: Color = Color(0xFFFF3B5C)
) {
    val badgeDescription = if (badgeCount > 0) localized(badgeTextKey, "count" to badgeCount) else null
    Column(
        modifier = modifier
            .widthIn(min = 64.dp, max = 88.dp)
            // Không cắt (clip) để huy hiệu ở góc ngoài không bị mất phần trên.
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (badgeDescription != null) stateDescription = badgeDescription }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .a1Card(radius = 20.dp, depth = 4.dp, background = if (highlight) A1Palette.Highlight else Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
            }
            val shown = when {
                badgeCount > 0 -> if (badgeCount > 99) "99+" else badgeCount.toString()
                else -> badgeText
            }
            shown?.let {
                A1Badge(it, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp), color = if (badgeCount > 0) Color(0xFFFF3B5C) else badgeColor)
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MaterialTheme.typography.titleSmall.fontFamily,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .background(A1Palette.Ink, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 1.dp)
        )
    }
}

internal fun LeaderboardSnapshot.homeRank(): Int? =
    seasonCurrentPlayer?.rank

@Composable
private fun ArcadePlayerSummary(
    displayName: String,
    avatarId: String?,
    avatarImageUrl: String,
    frameId: String,
    level: Int,
    currentXp: Int,
    nextXp: Int,
    elo: Int?,
    tier: String?,
    onOpenProfile: () -> Unit
) {
    val levelDescription = localized(TextKey.Level, "level" to level)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_season_card")
            .a1Card()
            .clip(RoundedCornerShape(A1Dimens.CardRadius))
            .clickable(role = Role.Button, onClick = onOpenProfile)
            .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar có khung + huy hiệu cấp ở góc dưới phải (theo thiết kế).
        Box {
            PlayerAvatar(
                displayName = displayName,
                avatarId = avatarId,
                frameId = frameId,
                size = 84.dp,
                imageUrl = avatarImageUrl
            )
            com.hienthai.fastowin.ui.components.A1LevelBadge(level, Modifier.align(Alignment.BottomEnd))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                displayName,
                style = MaterialTheme.typography.titleLarge,
                color = A1Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("home_header_content")
            )
            Row(
                modifier = Modifier.testTag("home_season_tier"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                elo?.let {
                    Text(
                        "Elo $it",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = A1Palette.Muted
                    )
                }
                Text(
                    tier?.replaceFirstChar { it.uppercase() } ?: localized(TextKey.PlacementPending),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = MaterialTheme.typography.titleSmall.fontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF7A4D00),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(A1Palette.Highlight, RoundedCornerShape(11.dp))
                        .border(2.dp, Color(0xFFE0A100), RoundedCornerShape(11.dp))
                        .padding(horizontal = 10.dp, vertical = 1.dp)
                )
            }
            A1ProgressBar(progress = currentXp.toFloat() / nextXp.coerceAtLeast(1), height = 10.dp)
        }
    }
}

/** Thanh tiến độ A1: rãnh xanh nhạt viền navy, phần đã đạt màu vàng. */
@Composable
internal fun A1ProgressBar(progress: Float, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 12.dp) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(A1Palette.SurfaceAlt2, shape)
            .border(2.dp, A1Palette.Line, shape)
            .padding(2.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(A1Palette.Yellow1, A1Palette.Yellow3)), shape)
        )
    }
}

/**
 * Thanh tab A1: nền trắng, viền trên navy; nút "Chơi" vàng nổi ở giữa.
 * Thứ tự theo thiết kế: Trang chủ · Hạng · Chơi · Bang · Tôi. Tab "Chơi" dùng lại [MainTab.ROOMS].
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun FastToWinBottomBar(
    selected: MainTab,
    friendNotificationCount: Int,
    unclaimedGiftCount: Long = 0,
    onHome: () -> Unit,
    onRooms: () -> Unit,
    onLeaderboard: () -> Unit,
    onClan: () -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationHeight = 81.dp + 40.dp * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bottom_bar")
            .background(Color.White)
            .drawBehind {
                drawRect(A1Palette.Line, size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx()))
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .padding(top = 3.dp)
                .height(navigationHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarItem(
                tab = MainTab.HOME,
                label = localized(TextKey.Home),
                icon = Icons.Rounded.Home,
                selected = selected == MainTab.HOME,
                onClick = onHome,
                modifier = Modifier.weight(1f),
                giftBadgeCount = unclaimedGiftCount
            )
            BottomBarItem(
                MainTab.LEADERBOARD,
                localized(TextKey.TabRank),
                Icons.Rounded.Leaderboard,
                selected == MainTab.LEADERBOARD,
                onLeaderboard,
                Modifier.weight(1f)
            )
            BottomBarItem(
                tab = MainTab.ROOMS,
                label = localized(TextKey.TabPlay),
                icon = Icons.Rounded.PlayArrow,
                selected = selected == MainTab.ROOMS,
                onClick = onRooms,
                modifier = Modifier.weight(1f),
                raised = true
            )
            BottomBarItem(
                MainTab.CLAN,
                localized(TextKey.TabClan),
                Icons.Rounded.Shield,
                selected == MainTab.CLAN,
                onClan,
                Modifier.weight(1f)
            )
            BottomBarItem(
                MainTab.ACCOUNT,
                localized(TextKey.TabMe),
                Icons.Rounded.Person,
                selected == MainTab.ACCOUNT,
                onAccount,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: MainTab,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    giftBadgeCount: Long = 0,
    raised: Boolean = false
) {
    val giftDescription = if (giftBadgeCount > 0) localized(TextKey.GiftUnclaimedCount, "count" to giftBadgeCount) else null
    Column(
        modifier = modifier
            .fillMaxHeight()
            // Không có hiệu ứng gợn sóng khi bấm tab (theo yêu cầu); trạng thái chọn đã đủ phản hồi.
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .testTag("bottom_tab:${tab.name.lowercase()}")
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (giftDescription != null) {
                    stateDescription = giftDescription
                    liveRegion = LiveRegionMode.Polite
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (raised) 8.dp else 1.dp, Alignment.Bottom)
    ) {
        if (raised) {
            // Nút "Chơi" nổi lên khỏi thanh tab, nhãn nằm bên dưới như các tab khác.
            Box(
                modifier = Modifier
                    // Chỉ chiếm 30dp trong thanh tab, phần còn lại nhô lên trên để nhãn "Chơi" luôn hiện.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val reserved = 33.dp.roundToPx()
                        layout(placeable.width, reserved) { placeable.place(0, reserved - placeable.height) }
                    }
                    .size(width = 62.dp, height = 63.dp)
                    .padding(bottom = 0.dp)
                    .a1Card(radius = 20.dp, depth = 5.dp, background = Color.Transparent)
                    .background(
                        Brush.verticalGradient(listOf(A1Palette.Yellow1, A1Palette.Yellow3)),
                        RoundedCornerShape(20.dp)
                    )
                    .border(3.dp, A1Palette.Line, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = A1Palette.YellowBorder, modifier = Modifier.size(38.dp))
            }
        } else {
            Box {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 34.dp)
                        .then(
                            if (selected) Modifier
                                .background(A1Palette.Highlight, RoundedCornerShape(12.dp))
                                .border(2.dp, A1Palette.Line, RoundedCornerShape(12.dp))
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = A1Palette.Line, modifier = Modifier.size(26.dp))
                }
                HomeCountBadge(giftBadgeCount, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MaterialTheme.typography.titleSmall.fontFamily,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) A1Palette.Ink else A1Palette.Muted,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun HomeQuickAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Long = 0,
    badgeTextKey: TextKey = TextKey.GiftUnclaimedCount
) {
    val badgeDescription = if (badgeCount > 0) localized(badgeTextKey, "count" to badgeCount) else null
    Box(
        modifier = modifier
            .heightIn(min = 84.dp)
            .testTag("home_action:$title")
            .a1Card(radius = 18.dp, depth = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (badgeDescription != null) stateDescription = badgeDescription }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 78.dp)
                .testTag("home_action_content:$title"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(accent, RoundedCornerShape(14.dp))
                        .border(2.dp, A1Palette.Line, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, modifier = Modifier.size(26.dp), tint = Color.White)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = A1Palette.Ink, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        HomeCountBadge(badgeCount)
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The parent control announces the exact contextual count; the visual cap remains decorative. */
@Composable
private fun HomeCountBadge(count: Long, modifier: Modifier = Modifier) {
    if (count <= 0) return
    A1Badge(if (count > 99) "99+" else count.toString(), modifier)
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun GameModePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    playerLevel: Int = 1,
    requiresOnlineAdmission: Boolean = true,
    onSelect: (GameMode) -> Unit
) {
    ArcadeDialog(
        title = title.uppercase(),
        subtitle = localized(TextKey.HarderModesUnlock),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GameMode.entries.filter { !it.isLegacy && it != GameMode.TEAM_2V2 }.forEach { mode ->
                val unlocked = playerLevel >= mode.unlockLevel
                ModeChoice(
                    title = localized(mode.titleKey),
                    subtitle = if (unlocked) localized(mode.descriptionKey) else localized(TextKey.UnlockAtLevel, "level" to mode.unlockLevel),
                    icon = mode.modeIcon(),
                    enabled = gameModeChoiceEnabled(mode, playerLevel, requiresOnlineAdmission, LocalNewOnlineIntentAllowed.current),
                    modifier = Modifier.testTag("game_mode:${mode.name}")
                ) { onSelect(mode) }
            }
        }
        ArcadeActionButton(
            label = localized(TextKey.Close).uppercase(),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            style = ArcadeActionStyle.OUTLINE
        )
    }
}

internal fun gameModeChoiceEnabled(
    mode: GameMode,
    playerLevel: Int,
    requiresOnlineAdmission: Boolean,
    newOnlineIntentAllowed: Boolean
): Boolean = playerLevel >= mode.unlockLevel && (!requiresOnlineAdmission || newOnlineIntentAllowed)

@Composable
fun MatchTypePickerDialog(
    title: String,
    quota: PlayQuotaSnapshot? = null,
    onQuotaExhausted: () -> Unit = {},
    onDismiss: () -> Unit,
    onSelect: (MatchType) -> Unit
) {
    ArcadeDialog(
        title = title.uppercase(),
        subtitle = localized(TextKey.ChooseMatchScoring),
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val remainingMatches = quota?.dailyMatchesRemaining ?: 0
            val totalMatches = (quota?.baseMatches ?: 10) + (quota?.bonusMatchesGranted ?: 0)
            Text(
                text = localized(
                    TextKey.OnlineMatchesRemaining,
                    "remaining" to remainingMatches,
                    "total" to totalMatches
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("online_quota_remaining")
                    .semantics { stateDescription = "$remainingMatches/$totalMatches" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = A1Palette.YellowBorder
            )
            if (quota != null && quota.bankedMatches > 0) {
                Text("${localized(TextKey.BankedPlays)} · ${quota.bankedMatches}")
            }
            ModeChoice(
                title = localized(TextKey.CasualMatch),
                subtitle = localized(TextKey.CasualMatchDescription),
                icon = CrossedSwordsIcon,
                enabled = LocalNewOnlineIntentAllowed.current,
                modifier = Modifier.testTag("match_type:CASUAL"),
                onClick = {
                    if (quota?.remainingMatches == 0) onQuotaExhausted()
                    else onSelect(MatchType.CASUAL)
                }
            )
            ModeChoice(
                title = localized(TextKey.RankedMatch),
                subtitle = localized(TextKey.RankedMatchDescription),
                icon = Icons.Default.EmojiEvents,
                enabled = LocalNewOnlineIntentAllowed.current,
                modifier = Modifier.testTag("match_type:RANKED"),
                onClick = {
                    if (quota?.remainingMatches == 0) onQuotaExhausted()
                    else onSelect(MatchType.RANKED)
                }
            )
        }
        ArcadeActionButton(
            label = localized(TextKey.Close).uppercase(),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            style = ArcadeActionStyle.OUTLINE
        )
    }
}

@Composable
private fun ModeChoice(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp).alpha(if (enabled) 1f else 0.58f),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        contentColor = A1Palette.Ink,
        border = BorderStroke(2.dp, A1Palette.Line)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (enabled) A1Palette.Blue2 else A1Palette.SurfaceAlt2,
                border = BorderStroke(2.dp, A1Palette.Line)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(24.dp), tint = if (enabled) Color.White else A1Palette.Muted)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = A1Palette.Ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = A1Palette.Muted)
            }
            Icon(
                imageVector = if (enabled) Icons.Default.ChevronRight else Icons.Default.Lock,
                contentDescription = null,
                tint = if (enabled) A1Palette.Line else A1Palette.Muted
            )
        }
    }
}

internal fun GameMode.modeIcon(): ImageVector = when (this) {
    GameMode.ORDER -> Icons.Rounded.Bolt
    GameMode.RANDOM_TARGET -> Icons.Rounded.Shuffle
    GameMode.TIME_BONUS, GameMode.TIME_ATTACK -> Icons.Rounded.Timer
    GameMode.SPEED_UP -> Icons.Rounded.Speed
    GameMode.SURVIVAL -> Icons.Rounded.Favorite
    GameMode.COMBO -> Icons.Rounded.LocalFireDepartment
    GameMode.TEAM_2V2 -> Icons.Default.Group
}

private fun connectionKey(status: ConnectionStatus): TextKey = when (status) {
    ConnectionStatus.DISCONNECTED -> TextKey.ConnectionDisconnected
    ConnectionStatus.CONNECTING -> TextKey.ConnectionConnecting
    ConnectionStatus.AUTHENTICATING -> TextKey.ConnectionAuthenticating
    ConnectionStatus.CONNECTED -> TextKey.ConnectionConnected
    ConnectionStatus.RECONNECTING -> TextKey.ConnectionReconnecting
    ConnectionStatus.TERMINAL -> TextKey.ConnectionDisconnected
}
