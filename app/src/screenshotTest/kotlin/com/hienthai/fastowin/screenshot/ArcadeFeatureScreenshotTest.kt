package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.platform.StoreBillingState
import com.hienthai.fastowin.platform.StoreProductPrice
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.MissionState
import com.hienthai.fastowin.ui.screens.ClanScreen
import com.hienthai.fastowin.ui.screens.LeaderboardScreen
import com.hienthai.fastowin.ui.screens.NotificationsScreen
import com.hienthai.fastowin.ui.screens.ProfileSection
import com.hienthai.fastowin.ui.screens.ProfileSectionScreen
import com.hienthai.fastowin.ui.screens.ShopScreen
import com.hienthai.fastowin.ui.screens.ShopTab
import com.hienthai.fastowin.ui.screens.TournamentScreen

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun shopGemCatalog() = ArcadeScreenshotFrame {
    val packages = ArcadeScreenshotFixtures.gemPackages()
    ShopScreen(
        progression = ArcadeScreenshotFixtures.profile().progression,
        onClose = {},
        gemPackages = packages,
        billingState = StoreBillingState(
            platform = StorePlatform.GOOGLE_PLAY,
            isReady = true,
            prices = packages.associate { item ->
                item.productId to StoreProductPrice(item.productId, "49.000 ₫")
            }
        ),
        isAccount = true,
        initialTab = ShopTab.GEMS,
        onOpenPurchaseHistory = {}
    )
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun shopGoldCatalog() = ArcadeScreenshotFrame {
    val packages = ArcadeScreenshotFixtures.gemPackages()
    ShopScreen(
        progression = ArcadeScreenshotFixtures.profile().progression,
        onClose = {},
        gemPackages = packages,
        billingState = StoreBillingState(
            platform = StorePlatform.GOOGLE_PLAY,
            isReady = true,
            prices = packages.associate { item ->
                item.productId to StoreProductPrice(item.productId, "₫49.000")
            }
        ),
        isAccount = true,
        initialTab = ShopTab.GOLD
    )
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun shopPlayCredits() = ArcadeScreenshotFrame {
    ShopScreen(
        progression = ArcadeScreenshotFixtures.profile().progression,
        onClose = {},
        isAccount = true,
        initialTab = ShopTab.PLAYS,
        quota = PlayQuotaSnapshot(
            quotaDate = "2026-11-14",
            baseMatches = 5,
            matchesConsumed = 3,
            bonusMatchesGranted = 0,
            remainingMatches = 17,
            nextResetAtEpochMillis = ArcadeScreenshotFixtures.NOW_MILLIS + 86_400_000,
            bankedMatches = 15
        ),
        onExchangePlays = { _, _ -> }
    )
}

@PreviewTest
@Preview(name = "plays_large_text", widthDp = 375, heightDp = 1100, fontScale = 1.6f)
@Composable
fun shopPlayCreditsLargeText() = shopPlayCredits()

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun playerLeaderboard() = ArcadeScreenshotFrame {
    LeaderboardScreen(
        state = ArcadeScreenshotFixtures.leaderboardState(),
        onBack = {},
        onRefresh = {},
    )
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun clanDetail() = ArcadeScreenshotFrame {
    val state = ArcadeScreenshotFixtures.clanState()
    ClanScreen(
        serverUrl = "",
        currentUserId = state.profile?.userId,
        myClanId = state.profile?.clanId,
        clanList = state.clanList,
        pendingJoinClanIds = emptySet(),
        currentClan = state.currentClan,
        notice = null,
        onCreateClan = { _, _ -> },
        onJoinClan = {},
        onLeaveClan = {},
        onSearch = {},
        onKickMember = { _, _ -> },
        onRespondJoinRequest = { _, _, _ -> },
        onUpdateLogo = { _, _ -> },
        onClaimQuest = {},
        missionState = MissionState(items = listOf(MissionInstanceView(
            instanceId = "legacy-clan-period", code = "LEGACY_CLAN_QUEST", scope = MissionScope.CLAN,
            action = MissionAction.WIN, period = MissionCadence.WEEKLY, difficulty = MissionDifficulty.NORMAL,
            periodStart = "1970-01-01", periodEndExclusive = "1970-01-05", target = 350, progress = 261,
            personalContribution = 86, reward = MissionReward(xp = 800, gold = 5000, gems = 10),
            state = MissionClaimState.IN_PROGRESS, titleKey = "MissionActionWIN", titleArguments = mapOf("target" to "350")
        ))),
        onViewClan = {},
        onBack = {},
        gold = state.profile?.progression?.gold ?: 0,
        gems = state.profile?.progression?.gems ?: 0,
        unreadNotifications = state.unreadNotificationCount
    )
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun runningTournament() = ArcadeScreenshotFrame {
    TournamentScreen(
        state = ArcadeScreenshotFixtures.tournamentState(),
        onBack = {},
        onCreate = { _, _, _, _, _ -> },
        onInvite = {},
        onRespondInvitation = { _, _ -> },
        onStart = {},
        onLeave = {},
        onOpenFriendProfile = {}
    )
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun notificationInbox() = ArcadeScreenshotFrame {
    NotificationsScreen(
        notifications = ArcadeScreenshotFixtures.notifications(),
        onBack = {},
        onOpen = {},
        onDismiss = {},
        onMarkAllRead = {},
        onClearAll = {},
        nowMillis = ArcadeScreenshotFixtures.NOW_MILLIS
    )
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun clanDiscovery() = ArcadeScreenshotFrame {
    ClanScreen(
        serverUrl = "",
        currentUserId = "player-1",
        myClanId = null,
        clanList = listOf(
            com.hienthai.fastowin.protocol.ClanSummarySnapshot("c1", "Thần Tốc Việt", 46, 50, 6449, logoId = "eagle", level = 18),
            com.hienthai.fastowin.protocol.ClanSummarySnapshot("c2", "Sói Đêm", 30, 50, 4102, logoId = "wolf", level = 12),
            com.hienthai.fastowin.protocol.ClanSummarySnapshot("c3", "Vương Miện 50", 21, 40, 2876, logoId = "crown", level = 9)
        ),
        pendingJoinClanIds = setOf("c2"),
        currentClan = null,
        notice = null,
        onCreateClan = { _, _ -> },
        onJoinClan = {},
        onLeaveClan = {},
        onSearch = {},
        onKickMember = { _, _ -> },
        onRespondJoinRequest = { _, _, _ -> },
        onUpdateLogo = { _, _ -> },
        onClaimQuest = {},
        onViewClan = {},
        onBack = {},
        showBackButton = false
    )
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun friendsScreen() = ArcadeScreenshotFrame {
    val base = ArcadeScreenshotFixtures.homeState()
    com.hienthai.fastowin.ui.screens.FriendsScreen(
        state = base.copy(
            social = com.hienthai.fastowin.protocol.FriendsSnapshot(
                friends = listOf(
                    com.hienthai.fastowin.protocol.FriendSnapshot("f1", "Lan", "LAN001", "female",
                        com.hienthai.fastowin.protocol.FriendPresence.ONLINE, "frame_gold"),
                    com.hienthai.fastowin.protocol.FriendSnapshot("f2", "Khoa Combo", "KHOA02", "male",
                        com.hienthai.fastowin.protocol.FriendPresence.PLAYING, "frame_platinum"),
                    com.hienthai.fastowin.protocol.FriendSnapshot("f3", "Dũng Tia Chớp", "DUNG03", "male",
                        com.hienthai.fastowin.protocol.FriendPresence.OFFLINE, "frame_bronze")
                ),
                incomingRequests = listOf(
                    com.hienthai.fastowin.protocol.FriendRequestSnapshot("r1", "u9", "VyVy", "VYVY09", "female", "frame_silver")
                )
            ),
            roomInvitations = listOf(
                com.hienthai.fastowin.protocol.ServerMessage.RoomInvitation(
                    invitationId = "i1", fromUserId = "u7", fromDisplayName = "Minh",
                    roomId = "room-1", roomName = "Phòng của Minh", expiresAtEpochMillis = 0L
                )
            )
        ),
        onBack = {}, onRefresh = {}, onSendRequest = {}, onRespondRequest = { _, _ -> }, onCancelRequest = {},
        onRemoveFriend = {}, onBlockPlayer = {}, onUnblockPlayer = {}, onInviteFriend = {},
        onRespondRoomInvitation = { _, _ -> }, onOpenFriendProfile = {}
    )
}
