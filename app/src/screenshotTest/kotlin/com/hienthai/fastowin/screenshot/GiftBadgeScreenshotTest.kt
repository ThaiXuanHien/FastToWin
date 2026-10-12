package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.ui.screens.LobbyScreen

@PreviewTest
@Preview(name = "phone375", widthDp = 375, heightDp = 812, showSystemUi = false)
@Preview(name = "phone320_large", widthDp = 320, heightDp = 812, fontScale = 1.6f, showSystemUi = false)
@Preview(name = "landscape", widthDp = 844, heightDp = 390, showSystemUi = false)
@Preview(name = "desktop", widthDp = 1024, heightDp = 900, showSystemUi = false)
@Composable
fun giftBadgeHome() = ArcadeScreenshotFrame { GiftBadgeHomeContent(7) }

@PreviewTest
@Preview(name = "large_count_english", widthDp = 375, heightDp = 1500, fontScale = 1.3f, showSystemUi = false)
@Preview(name = "desktop_english", widthDp = 1024, heightDp = 1200, showSystemUi = false)
@Composable
fun giftBadgeLargeCount() = ArcadeScreenshotFrame {
    ProvideLocalization(AppLanguage.ENGLISH) { GiftBadgeHomeContent(123) }
}

@PreviewTest
@Preview(name = "zero_phone", widthDp = 375, heightDp = 812, showSystemUi = false)
@Composable
fun giftBadgeZero() = ArcadeScreenshotFrame { GiftBadgeHomeContent(0) }

@PreviewTest
@Preview(name = "guest_phone", widthDp = 375, heightDp = 812, showSystemUi = false)
@Composable
fun giftBadgeGuest() = ArcadeScreenshotFrame { GiftBadgeHomeContent(7, isGuest = true) }

@Composable
private fun GiftBadgeHomeContent(count: Long, isGuest: Boolean = false) {
    LobbyScreen(
        state = ArcadeScreenshotFixtures.homeState(), onModeSelected = {}, onStartMatchmaking = { _, _ -> },
        onCancelMatchmaking = {}, onOpenRoomBrowser = {}, onCreateRoom = { _, _, _, _ -> }, onJoinRoom = { _, _ -> },
        onLeaveRoom = {}, onSetReady = {}, onKickOpponent = {}, onRefreshRooms = {}, onOpenProfile = {},
        onOpenLeaderboard = {}, onOpenFriends = {}, onOpenFriendProfile = {}, onBackToMode = {}, onLogout = {},
        isGuest = isGuest, onUpgradeGuest = {}, onOpenNotifications = {}, onOpenGifts = {}, unclaimedGiftCount = count,
        onOpenClan = {}, onOpenPractice = {}, onOpenTournament = {}, onOpenShop = {},
        onShareRoom = { _, _ -> Result.success(Unit) }, onResolveRoomLink = {}, onClaimDailyCheckIn = {}
    )
}
